# build-jar-tools
使用方法：
1.在项目build.gradle中添加：

    buildscript {
    
        repositories {
            ...
            maven { url 'https://dl.bintray.com/near-2009/maven/' }
        }
        dependencies {
            ...
            classpath 'com.tools:build-jar-tools:1.0.0'
        }
    }

在libs module的builde.gradle中添加

    buildJar {
       //输出目录
        outputFileDir = project.buildDir.path + "/outputs/jar"
       //Jar包名
       outputFileName = project.name
       //版本号
       versionCode = "1.0.0"
       //混淆配置
       proguardConfigFile = "proguard-rules.pro"
       //是否需要默认的混淆配置proguard-android.txt
       needDefaultProguard = true
       //第三方依赖jar包
       //includeJar = [
       //      "libs/retrofit-2.3.0.jar",
       //      "libs/okhttp-3.8.1.jar",
       //      "libs/okio-1.13.0.jar",
       //      "libs/fastjson-1.2.37.jar",
       //      "libs/converter-fastjson-android-2.1.0.jar"
       //]
    }

2.执行命令：

    ./gradlew buildJar 打普通Jar包

    ./gradlew buildProguardJar 打混淆Jar包
