package com.tools

import com.android.SdkConstants
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.bundling.Jar
import proguard.gradle.ProGuardTask

class LibraryBuildJarPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "buildJar";

    @Override
    public void apply(Project project) {
        DefaultDomainObjectSet<BaseVariant> variants
        if (project.getPlugins().hasPlugin(LibraryPlugin)) {
            variants = project.android.libraryVariants;
            project.extensions.create(EXTENSION_NAME, LibraryBuildJarExtension);
            applyTask(project, variants);
        }
    }

    private void applyTask(Project project, variants) {
        project.afterEvaluate {
            //加载构建参数
            LibraryBuildJarExtension jarExtension = LibraryBuildJarExtension.getConfig(project);
            def includeJar = jarExtension.includeJar
            def includePackage = jarExtension.includePackage
            def includeClass = jarExtension.includeClass
            def excludeClass = jarExtension.excludeClass
            def excludePackage = jarExtension.excludePackage
            def excludeJar = jarExtension.excludeJar
            Set<Task> buildJarTasks = new HashSet<>()
            Set<Task> buildProguardJarTasks = new HashSet<>()
            def file = new File(jarExtension.outputFileDir)
            if (!file.exists()) {
                file.mkdirs()
            }
            //遍历build type,创建对应build jar task
            variants.all { variant ->
                //获取compile${variant.name.capitalize()}JavaWithJavac任务
                def compileTaskName = BuildJarUtils.getCompileJavaTaskName(project, variant)
                def compileTask = project.tasks.findByName(compileTaskName)
                if (compileTask != null) {
                    def buildJarAfterCompile = "build${variant.name.capitalize()}JarAfterCompile"
                    //创建编译任务compileTask
                    def buildJar = project.tasks.create("build${variant.name.capitalize()}Jar", Jar)
                    buildJarTasks.add(buildJar)
                    buildJar.setDescription("构建jar包")
                    //配置输出文件名
                    def archiveName = "${jarExtension.outputFileName}-${variant.buildType.name}-${jarExtension.versionCode}${SdkConstants.DOT_JAR}"
                    if (!variant.productFlavors.empty) {
                        archiveName = "${jarExtension.outputFileName}-${variant.buildType.name}-${jarExtension.versionCode}-${variant.productFlavors.first().name}${SdkConstants.DOT_JAR}"
                    }
                    //配置构建jar包的数据规则
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

                        if (includeJar != null && includeJar.size() > 0) {
                            includeJar.each {
                                //添加第三方Jar包依赖
                                if (it.endsWith(SdkConstants.DOT_JAR) && !BuildJarUtils.isExcludedJar(it, excludeJar)) {
                                    //添加jar包
                                    buildJar.from(project.zipTree(it))
                                }
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
                    //创建build jar task
                    project.task(buildJarAfterCompile) << {
                        //获取class文件及Jar包依赖文件
                        Set<File> inputFiles = BuildJarUtils.getCompileJavaTaskOutputFiles(project, variant, compileTask)

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
                    //创建build jar 混淆任务
                    ProguardConfig config = new ProguardConfig()
                    config.dependTask = buildJar
                    config.inJars = jarExtension.outputFileDir + "/${archiveName}"
                    config.outJars = jarExtension.outputFileDir + "/proguard_${archiveName}"
                    config.proguardConfigFile = jarExtension.proguardConfigFile
                    config.applyMappingFile = jarExtension.applyMappingFile
                    config.needDefaultProguard = jarExtension.needDefaultProguard
                    config.mappingOutput = jarExtension.outputFileDir + "/mapping.txt"

                    Task buildProguardTask = createProguardTask(project, variant, config)
                    buildProguardJarTasks.add(buildProguardTask)

                    def buildJarAfterCompileTask = project.tasks[buildJarAfterCompile]
                    buildJarAfterCompileTask.dependsOn compileTask
                    buildJar.dependsOn buildJarAfterCompileTask
                    buildJar.doFirst(buildJarClosure)
                }
            }

            def buildAllJarTask = project.tasks.create("buildJar")
            buildJarTasks.each {
                buildAllJarTask.dependsOn it
            }
            def buildAllProguardJarTask = project.tasks.create("buildProguardJar")
            buildProguardJarTasks.each {
                buildAllProguardJarTask.dependsOn it
            }
        }

    }

    private Task createProguardTask(Project project, BaseVariant variant, ProguardConfig config) {
        //创建混淆任务
        def buildProguardJar = project.tasks.create("build${variant.name.capitalize()}ProguardJar", ProGuardTask);
        buildProguardJar.setDescription("混淆jar包")
        buildProguardJar.dependsOn config.dependTask
        //设置不删除未引用的资源(类，方法等)
        buildProguardJar.dontshrink();
        //忽略警告
        buildProguardJar.ignorewarnings()
        //需要被混淆的jar包
        buildProguardJar.injars(config.inJars)
        //混淆后输出的jar包
        buildProguardJar.outjars(config.outJars)

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
        buildProguardJar.configuration(config.proguardConfigFile)
        if (config.needDefaultProguard) {
            buildProguardJar.configuration(project.android.getDefaultProguardFile('proguard-android.txt'))
        }
        //applymapping
        def applyMappingFile = config.applyMappingFile
        if (applyMappingFile != null) {
            buildProguardJar.applymapping(applyMappingFile)
        }
        //输出mapping文件
        buildProguardJar.printmapping(config.mappingOutput)
        return buildProguardJar
    }

    class ProguardConfig {
        @Input
        Task dependTask //依赖Task
        @Input
        String inJars //数据源Jar包路径 jarExtension.outputFileDir + "/" + dependTask.archiveName
        @Input
        String outJars //输出Jar包路径 jarExtension.outputFileDir + "/proguard_" + dependTask.archiveName
        @Input
        String proguardConfigFile //混淆配置
        @Input
        String applyMappingFile //applyMapping
        @Input
        String mappingOutput //mapping文件输出路径
        @Input
        boolean needDefaultProguard //是否需要默认的混淆配置proguard-android.txt

        public ProguardConfig() {
        }
    }
}
