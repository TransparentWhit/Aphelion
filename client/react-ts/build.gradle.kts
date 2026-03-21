plugins {
    alias(libs.plugins.node)
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildReact") {
    dependsOn(tasks.npmInstall)
    npmCommand.set(listOf("build"))
    inputs.dir("node_modules")
    inputs.dir("public")
    inputs.dir("src")
    inputs.file("index.html")
    inputs.file("eslint.config.ts")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    inputs.file("tsconfig.app.json")
    inputs.file("tsconfig.json")
    inputs.file("tsconfig.node.json")
    inputs.file("vite.config.ts")
    outputs.dir(layout.buildDirectory.dir("dist"))
}
