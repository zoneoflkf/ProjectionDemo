plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileProto()

    namespace = "com.lkf.remotecontrol"
    compileSdk = 28

    defaultConfig {
        applicationId = "com.lkf.remotecontrol"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.2.4")
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.google.protobuf:protobuf-java:3.21.7")
    implementation("com.google.protobuf:protoc:3.21.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.java-websocket:Java-WebSocket:1.5.4")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

var hasCompiledProto = false

//编译proto文件,注意:需要安装protoc编译工具
fun compileProto() {
    if (hasCompiledProto) {
        println("compileProto: Has compiled")
        return
    }
    hasCompiledProto = true

    val protoSrcDir = File(projectDir, "src/main/proto").apply { mkdirs() }
    val protoJavaOut = File(projectDir, "src/main/java").apply { mkdirs() }

    println("compileProtobuf begin -> srcDir:$protoSrcDir | outDir:$protoJavaOut")

    var fileCount = 0
    protoSrcDir.listFiles()?.forEach { protoFile ->
        if (protoFile.isFile && protoFile.name.endsWith("proto")) {
            ++fileCount

            //val protocPath = "C:\\protoc-24.4-win64\\bin\\protoc"
            val protocPath = "protoc"
            val cmd = "$protocPath -I=$protoSrcDir --java_out=$protoJavaOut ${protoFile.path}"
            println("compileProtobuf -> compileFile:${protoFile.name} | cmd:$cmd")
            Runtime.getRuntime().exec(cmd)
        }
    }

    println("compileProtobuf end -> fileCount:$fileCount")
}