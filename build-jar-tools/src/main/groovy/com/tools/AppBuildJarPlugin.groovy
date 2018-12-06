package com.tools

import com.android.SdkConstants
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.bundling.Jar
import proguard.gradle.ProGuardTask

class AppBuildJarPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "buildJar";

    @Override
    public void apply(Project project) {
        DefaultDomainObjectSet<BaseVariant> variants
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            variants = project.android.applicationVariants;
            project.extensions.create(EXTENSION_NAME, AppBuildJarExtension);
            applyTask(project, variants);
        }
    }

    private void applyTask(Project project, variants) {
        project.afterEvaluate {
            AppBuildJarExtension jarExtension = AppBuildJarExtension.getConfig(project);
            def includePackage = jarExtension.includePackage
            def includeClass = jarExtension.includeClass
            def excludeClass = jarExtension.excludeClass
            def excludePackage = jarExtension.excludePackage
            def excludeJar = jarExtension.excludeJar
            def file = new File(jarExtension.outputFileDir)
            if (!file.exists()) {
                file.mkdirs()
            }
            variants.all { variant ->
                if (variant.name.capitalize() == "Debug") {
                    //通过任务名获取编译Dex任务,eg:transformClassesWithDexForRelease
                    def dexTask = project.tasks.findByName(BuildJarUtils.getDexTaskName(project, variant))
                    if (dexTask != null) {
                        def buildJarBeforeDex = "buildJarBeforeDex${variant.name.capitalize()}"
                        //创建buildJar任务
                        def buildJar = project.tasks.create("buildJar", Jar)
                        buildJar.setDescription("构建jar包")
                        def archiveName = jarExtension.outputFileName + "_${variant.name.capitalize()}_" + jarExtension.versionCode + SdkConstants.DOT_JAR
                        Closure buildJarClosure = {
                            //过滤R文件和BuildConfig文件
                            buildJar.exclude("**/BuildConfig.class")
                            buildJar.exclude("**/BuildConfig\$*.class")
                            buildJar.exclude("**/R.class")
                            buildJar.exclude("**/R\$*.class")
                            buildJar.archiveName = archiveName
                            buildJar.destinationDir = project.file(jarExtension.outputFileDir)
                            if (excludeClass != null && excludeClass.size() > 0) {
                                excludeClass.each {
                                    //排除指定class
                                    buildJar.exclude(it)
                                }

                            }
                            if (excludePackage != null && excludePackage.size() > 0) {
                                excludePackage.each {
                                    //过滤指定包名下class
                                    buildJar.exclude("${it}/**/*.class")
                                }
                            }

                            if (includeClass != null && includeClass.size() > 0) {
                                includeClass.each {
                                    //打包指定class
                                    buildJar.include(it)
                                }
                            }

                            if (includePackage != null && includePackage.size() > 0) {
                                includePackage.each {
                                    //仅仅打包指定包名下class
                                    buildJar.include("${it}/**/*.class")
                                }

                            }
                            if ((includeClass == null || includeClass.size() <= 0) && (includePackage == null || includePackage.size() <= 0)) {
                                //当includeClass和includePackage都为空时默认全项目构建jar
                                buildJar.include("**/*.class")
                            }
                        }
                        project.task(buildJarBeforeDex) << {
                            //获取class文件及Jar包依赖文件
                            Set<File> inputFiles = BuildJarUtils.getDexTaskInputFiles(project, variant, dexTask)

                            inputFiles.each { inputFile ->
                                def path = inputFile.absolutePath
                                if (path.endsWith(SdkConstants.DOT_JAR) && !BuildJarUtils.isExcludedJar(path, excludeJar)) {
                                    //添加jar包
                                    buildJar.from(project.zipTree(path))
                                } else if (inputFile.isDirectory()) {
                                    //添加编译后的class文件，eg:intermediates/classes/debug
                                    buildJar.from(inputFile)
                                }
                            }
                        }

                        //创建混淆任务
                        def buildProguardJar = project.tasks.create("buildProguardJar", ProGuardTask);
                        buildProguardJar.setDescription("混淆jar包")
                        buildProguardJar.dependsOn buildJar
                        //设置不删除未引用的资源(类，方法等)
                        buildProguardJar.dontshrink();
                        //忽略警告
                        buildProguardJar.ignorewarnings()
                        //需要被混淆的jar包
                        buildProguardJar.injars(jarExtension.outputFileDir + "/" + archiveName)
                        //混淆后输出的jar包
                        buildProguardJar.outjars(jarExtension.outputFileDir + "/proguard_" + archiveName)

                        //libraryjars表示引用到的jar包不被混淆
                        // ANDROID PLATFORM
                        buildProguardJar.libraryjars(project.android.getSdkDirectory().toString() + "/platforms/" + "${project.android.compileSdkVersion}" + "/android.jar")
                        // JAVA HOME
                        def javaBase = System.properties["java.home"]
                        def javaRt = "/lib/rt.jar"
                        if (System.properties["os.name"].toString().toLowerCase().contains("mac")) {
                            if (!new File(javaBase + javaRt).exists()) {
                                javaRt = "/../Classes/classes.jar"
                            }
                        }
                        buildProguardJar.libraryjars(javaBase + "/" + javaRt)
                        //混淆配置文件
                        buildProguardJar.configuration(jarExtension.proguardConfigFile)
                        if (jarExtension.needDefaultProguard) {
                            buildProguardJar.configuration(project.android.getDefaultProguardFile('proguard-android.txt'))
                        }
                        //applymapping
                        def applyMappingFile = jarExtension.applyMappingFile
                        if (applyMappingFile != null) {
                            buildProguardJar.applymapping(applyMappingFile)
                        }
                        //输出mapping文件
                        buildProguardJar.printmapping(jarExtension.outputFileDir + "/" + "mapping.txt")
                        def buildJarBeforeDexTask = project.tasks[buildJarBeforeDex]
                        buildJarBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                        buildJar.dependsOn buildJarBeforeDexTask
                        buildJar.doFirst(buildJarClosure)
                    }
                }
            }
        }
    }
}
