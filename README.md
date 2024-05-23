# ProjectionDemo
利用闲暇（mo yu）时间，手搓了一个基于安卓的远程投屏+远程控制的Demo。（服务端+客户端）



# 【服务端】
端口号定义在ServerConfig类，可自行修改。



# 【客户端】
IP和端口号定义在ServerConfig类，注意改成自己的服务端IP和端口号。

ProjectionConfigs类中定义了投屏的参数，包括投屏最大尺寸限制，投屏帧率，以及推流的编码格式（默认为video/hevc，即H.265），详情参见源码。



# 【注意事项】
服务端和安卓客户端的开发语言都是Kotlin，本人的服务端开发环境是Windows + JDK-17 + Intellij；部署环境是Windows Server 2016。理论上环境随意，仅供参考。

客户端和服务端之间的通信使用了谷歌的Protobuf，gradle构建时如果报错找不到protoc命令，可以去下一个protoc编译工具。当然，也可以先屏蔽掉app的build.gradle.kts中的compileProto方法；后面要自己修改proto数据结构时再去安装protoc工具。

远程控制采用的是无障碍服务的方案，首次运行需要手动打开无障碍的APP权限。
