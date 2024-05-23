package com.lkf.remotecontrol.net.client

import com.lkf.remotecontrol.net.models.ProjectionRequest

interface IProjectionBiz {
    //请求当前在线设备列表
    fun requestOnlineDevices()

    //请求获取某个设备的投屏
    fun requestPullProjection(req: ProjectionRequest)

    //请求停止某个设备的投屏
    fun requestStopPullProjection(req: ProjectionRequest)
}