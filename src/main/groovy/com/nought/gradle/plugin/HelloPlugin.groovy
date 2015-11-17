package com.nought.gradle.plugin

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import proguard.gradle.ProGuardTask

class HelloPlugin implements Plugin<Project> {

    static final String PLUGIN_NAME = "helloPlugin"

    Project project
    HelloPluginExtension extension

    JavaCompile compileJavaSrc
    Jar jarLib
    ProGuardTask proguardLib
    Copy copyLib

    @Override
    void apply(Project project) {
        this.project = project
        this.extension = project.extensions.create(PLUGIN_NAME, HelloPluginExtension)

        project.afterEvaluate {
            createSomeTasks()
            // 如果是执行packageProguardJar任务，那么要提前关闭log开关
            if ('packageProguardJar' in project.gradle.startParameter.taskNames) {
                project.tasks.getByName("preBuild").doFirst {
                    enableLoggerDebug(false)
                }
            }
        }
    }

    private void createSomeTasks() {
        // Create a task to compile all java sources.
        compileJavaSrc = project.tasks.create("compileJava", JavaCompile);
        compileJavaSrc.setDescription("编译java源代码")
        compileJavaSrc.source = extension.javaSrcDir
        compileJavaSrc.include("com/nought/hellolib/**")
        compileJavaSrc.classpath = project.files([extension.androidJarDir + "/android.jar", extension.javaBase + "/" + extension.javaRt])
        compileJavaSrc.destinationDir = project.file(extension.classesOutDir)
        compileJavaSrc.sourceCompatibility = JavaVersion.VERSION_1_7
        compileJavaSrc.targetCompatibility = JavaVersion.VERSION_1_7
        compileJavaSrc.options.encoding = "UTF-8"
        compileJavaSrc.options.debug = false
        compileJavaSrc.options.verbose = false

        // Create a task to jar the classes.
        jarLib = project.tasks.create("jarLib", Jar);
        jarLib.setDescription("将class文件打包成jar")
        jarLib.dependsOn compileJavaSrc
        jarLib.archiveName = "helloLib.jar"
        jarLib.from(extension.classesOutDir)
        jarLib.destinationDir = project.file(extension.outputFileDir)
        jarLib.exclude("com/nought/hellolib/BuildConfig.class")
        jarLib.exclude("com/nought/hellolib/BuildConfig\$*.class")
        jarLib.exclude("**/R.class")
        jarLib.exclude("**/R\$*.class")
        jarLib.include("com/nought/hellolib/*.class")

        // Create a task to proguard the jar.
        proguardLib = project.tasks.create("proguardLib", ProGuardTask);
        proguardLib.setDescription("混淆jar包")
        proguardLib.dependsOn jarLib
        proguardLib.injars(extension.outputFileDir + "/" + "helloLib.jar")
        proguardLib.outjars(extension.outputFileDir + "/" + extension.outputFileName)
        proguardLib.libraryjars(extension.androidJarDir + "/android.jar")
        proguardLib.libraryjars(extension.javaBase + "/" + extension.javaRt)
        proguardLib.configuration(extension.proguardConfigFile)
        proguardLib.printmapping(extension.outputFileDir + "/" + "helloLib.mapping")

        // Create a task to copy the jar.
        copyLib = project.tasks.create("copyLib", Copy);
        copyLib.setDescription("不混淆，仅拷贝jar包")
        copyLib.dependsOn jarLib
        copyLib.from(extension.outputFileDir)
        copyLib.into(extension.outputFileDir)
        copyLib.include("helloLib.jar")
        copyLib.rename("helloLib.jar", extension.outputFileName)

        def packageProguardJar = project.tasks.create("packageProguardJar");
        packageProguardJar.setDescription("打包混淆、关闭log开关的hello lib")
        // packageProguardJar任务作为一个钩子，依赖真正执行工作的proguardLib
        packageProguardJar.dependsOn proguardLib
        // 最后把log开关置回原来开发时的状态
        packageProguardJar.doLast {
            enableLoggerDebug(true)
        }

        def packageNoProguardJar = project.tasks.create("packageNoProguardJar");
        packageNoProguardJar.setDescription("打包不混淆、开启log开关的hello lib")
        // packageNoProguardJar任务作为一个钩子，依赖真正执行工作的copyLib
        packageNoProguardJar.dependsOn copyLib
    }


    // 开启/关闭Log开关
    def enableLoggerDebug(boolean flag) {
        def loggerFilePath = "src/main/java/com/nought/hellolib/UncleNought.java"
        def updatedDebug = new File(loggerFilePath).getText('UTF-8')
                .replaceAll("ENABLE_DEBUG\\s?=\\s?" + (!flag).toString(), "ENABLE_DEBUG = " + flag.toString())
        new File(loggerFilePath).write(updatedDebug, 'UTF-8')
        println(flag ? 'ENABLE_DEBUG : [true]' : 'ENABLE_DEBUG : [false]')
    }
}