package com.tools

import org.gradle.api.Project
import org.gradle.api.tasks.Input

class LibraryBuildJarExtension {
    @Input
    HashSet<String> includeJar = [];//需要输出jar的第三方Jar,当此参数且includeJar为空时，则默认为无第三方Jar包依赖
    @Input
    HashSet<String> includePackage = [];//需要输出jar的包名列表,当此参数且includeClass为空时，则默认全项目输出
    @Input
    HashSet<String> includeClass = [];//需要输出jar的类名列表,当此参数且includePackage为空时，则默认全项目输出
    @Input
    HashSet<String> excludeJar = [];//不需要输出jar的jar包
    @Input
    HashSet<String> excludeClass = [];//不需要输出jar的类名列表
    @Input
    HashSet<String> excludePackage = [];//不需要输出jar的包名列表
    @Input
    String outputFileDir //输出目录
    @Input
    String outputFileName//输出原始jar包名
    @Input
    String versionCode //Jar包版本号
    @Input
    String proguardConfigFile //混淆配置
    @Input
    String applyMappingFile //applyMapping
    @Input
    boolean needDefaultProguard //是否需要默认的混淆配置proguard-android.txt

    public static LibraryBuildJarExtension getConfig(Project project) {
        LibraryBuildJarExtension config =
                project.getExtensions().findByType(LibraryBuildJarExtension.class);
        if (config == null) {
            config = new LibraryBuildJarExtension();
        }
        return config;
    }

}
