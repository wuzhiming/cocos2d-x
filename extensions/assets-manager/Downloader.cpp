/****************************************************************************
 Copyright (c) 2013 cocos2d-x.org
 
 http://www.cocos2d-x.org
 
 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:
 
 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.
 
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ****************************************************************************/

#include "Downloader.h"
#include "AssetsManager.h"

#include <curl/easy.h>
#include <stdio.h>

#define USE_THREAD_POOL 1

#if USE_THREAD_POOL
#include "base/threadpool.hpp"
#define POOL() static_cast<cocos2d::threadpool::pool*>(this->_threadPool)
#endif

NS_CC_EXT_BEGIN

#define POOL_SIZE 6

#define BUFFER_SIZE         8192
#define MAX_FILENAME        512
#define LOW_SPEED_LIMIT     1L
#define LOW_SPEED_TIME      5L
#define MAX_REDIRS          2
#define SELECT_TIMEOUT      4

#define TEMP_EXT            ".temp"

static size_t curlWriteFunc(void *ptr, size_t size, size_t nmemb, void *userdata)
{
    FILE *fp = (FILE*)userdata;
    size_t written = fwrite(ptr, size, nmemb, fp);
    return written;
}

static int downloadProgressFunc(Downloader::ProgressData *ptr, double totalToDownload, double nowDownloaded, double totalToUpLoad, double nowUpLoaded)
{
    if (ptr->downloaded != nowDownloaded)
    {
        ptr->downloaded = nowDownloaded;
        
        Downloader::ProgressData data = *ptr;
        
        Director::getInstance()->getScheduler()->performFunctionInCocosThread([=]{
            std::shared_ptr<Downloader> downloader = data.downloader.lock();
            
            if (downloader)
            {
                auto callback = downloader->getProgressCallback();
                if (callback)
                {
                    callback(totalToDownload, nowDownloaded, data.url, data.customId);
                }
            }
            else
            {
                CCLOG("invalid callback.");
            }
        });
    }
    
    return 0;
}


Downloader::Downloader()
: _onError(nullptr)
, _onProgress(nullptr)
, _onSuccess(nullptr)
, _connectionTimeout(0)
{
#if USE_THREAD_POOL
    _threadPool = new threadpool::pool(POOL_SIZE);
#endif
}

Downloader::~Downloader()
{
#if USE_THREAD_POOL
    POOL()->wait();
    delete POOL();
#endif
}

int Downloader::getConnectionTimeout()
{
    return _connectionTimeout;
}

void Downloader::setConnectionTimeout(int timeout)
{
    if (timeout >= 0)
        _connectionTimeout = timeout;
}

void Downloader::notifyError(ErrorCode code, const std::string &msg/* ="" */, const std::string &customId/* ="" */)
{
    std::shared_ptr<Downloader> downloader = shared_from_this();
    Director::getInstance()->getScheduler()->performFunctionInCocosThread([=]{
        Error err;
        err.code = code;
        err.message = msg;
        err.customId = customId;
        if (downloader != nullptr && downloader->_onError != nullptr)
            downloader->_onError(err);
    });
}

std::string Downloader::getFileNameFromUrl(const std::string &srcUrl)
{
    // Find file name and file extension
    std::string filename;
    unsigned long found = srcUrl.find_last_of("/\\");
    if (found != std::string::npos)
        filename = srcUrl.substr(found+1);
    return filename;
}

void Downloader::clearDownloadData()
{
    while (_progDatas.size() != 0) {
        delete _progDatas.back();
        _progDatas.pop_back();
    }
    
    while (_files.size() != 0) {
        delete _files.back();
        _files.pop_back();
    }
}

void Downloader::prepareDownload(FileDescriptor *fDesc, const std::string &srcUrl, const std::string &storagePath, const std::string &customId)
{
    fDesc->fp = nullptr;
    fDesc->curl = nullptr;
    
    Error err;
    err.customId = customId;
    
    // Asserts
    // Find file name and file extension
    unsigned long found = storagePath.find_last_of("/\\");
    if (found != std::string::npos)
    {
        fDesc->name = storagePath.substr(found+1);
        fDesc->path = storagePath.substr(0, found+1);
    }
    else
    {
        err.code = ErrorCode::INVALID_URL;
        err.message = "Invalid url or filename not exist error: " + srcUrl;
        if (this->_onError) this->_onError(err);
        return;
    }
    
    // Create a file to save package.
    const std::string outFileName = storagePath + TEMP_EXT;
    fDesc->fp = fopen(outFileName.c_str(), "wb");
    if (!fDesc->fp)
    {
        err.code = ErrorCode::CREATE_FILE;
        err.message = "Can not create file " + outFileName;
        if (this->_onError) this->_onError(err);
    }
}

void Downloader::downloadAsync(const std::string &srcUrl, const std::string &storagePath, const std::string &customId/* = ""*/)
{
    FileDescriptor fDesc;
    prepareDownload(&fDesc, srcUrl, storagePath, customId);
    if (fDesc.fp != nullptr)
    {

#if USE_THREAD_POOL
        POOL()->schedule(std::bind(&Downloader::download, this, srcUrl, fDesc, customId));
#else
        auto t = std::thread(&Downloader::download, this, srcUrl, fDesc, customId);
        t.detach();
#endif
    }
}

void Downloader::downloadSync(const std::string &srcUrl, const std::string &storagePath, const std::string &customId/* = ""*/)
{
    FileDescriptor fDesc;
    prepareDownload(&fDesc, srcUrl, storagePath, customId);
    if (fDesc.fp != nullptr)
    {
        download(srcUrl, fDesc, customId);
    }
}

void Downloader::batchDownload(const std::unordered_map<std::string, Downloader::DownloadUnit> &units)
{
    for (auto it = units.cbegin(); it != units.cend(); ++it) {
        DownloadUnit unit = it->second;
        std::string srcUrl = unit.srcUrl;
        std::string storagePath = unit.storagePath;
        std::string customId = unit.customId;
        
#if USE_THREAD_POOL
        POOL()->schedule(std::bind(&Downloader::downloadSync, this, srcUrl, storagePath, customId));
#else
        auto t = std::thread(&Downloader::downloadSync, this, srcUrl, storagePath, customId);
        t.detach();
#endif
    }
}

void Downloader::download(const std::string &srcUrl, const FileDescriptor &fDesc, const std::string &customId)
{
    std::shared_ptr<Downloader> downloader = shared_from_this();

    ProgressData data;
    data.customId = customId;
    data.url = srcUrl;
    data.downloader = downloader;
    data.downloaded = 0;
    
    void *curl = curl_easy_init();
    if (!curl)
    {
        this->notifyError(ErrorCode::CURL_UNINIT, "Can not init curl");
        return;
    }
    
    // Download pacakge
    CURLcode res;
    curl_easy_setopt(curl, CURLOPT_URL, srcUrl.c_str());
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, curlWriteFunc);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, fDesc.fp);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS, false);
    curl_easy_setopt(curl, CURLOPT_PROGRESSFUNCTION, downloadProgressFunc);
    curl_easy_setopt(curl, CURLOPT_PROGRESSDATA, &data);
    curl_easy_setopt(curl, CURLOPT_FAILONERROR, true);
    if (_connectionTimeout) curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, _connectionTimeout);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, LOW_SPEED_LIMIT);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, LOW_SPEED_TIME);
    
    res = curl_easy_perform(curl);
    if (res != CURLE_OK)
    {
        AssetsManager::removeFile(fDesc.path + fDesc.name + TEMP_EXT);
        this->notifyError(ErrorCode::NETWORK, "Error when download file", customId);
    }
    else
    {
        AssetsManager::renameFile(fDesc.path, fDesc.name + TEMP_EXT, fDesc.name);
        Director::getInstance()->getScheduler()->performFunctionInCocosThread([srcUrl, customId, downloader]{
            if (downloader != nullptr && downloader->_onSuccess != nullptr)
                downloader->_onSuccess(srcUrl, customId);
        });
    }
    fclose(fDesc.fp);
    curl_easy_cleanup(curl);
    
}

NS_CC_EXT_END
