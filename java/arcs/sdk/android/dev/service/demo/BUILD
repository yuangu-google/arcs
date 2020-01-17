licenses(["notice"])

load(
    "//third_party/java/arcs/build_defs:build_defs.bzl",
    "arcs_manifest_bundle",
)
load("//tools/build_defs/android:rules.bzl", "android_binary")
load("//tools/build_defs/kotlin:rules.bzl", "kt_android_library")

android_binary(
    name = "demo",
    dexopts = [
        "--min-sdk-version=29",
        "--target-sdk-version=29",
    ],
    manifest = "AndroidManifest.xml",
    javacopts = ["-Xep:AndroidJdkLibsChecker:OFF"],
    licenses = ["notice"],
    deps = [
      ":lib"
    ],
)

kt_android_library(
    name = "lib",
    srcs = glob(["*.java", "*.kt"]),
        manifest = "//java/arcs/android/common:AndroidManifest.xml",
    assets = [":demo_manifest_bundle"],
    assets_dir = "",
    resource_files = glob(["res/**"]),
    deps = [
              "//java/arcs/sdk/android/dev/api:api-android",
              "//java/arcs/sdk/android/dev/service",
              "//third_party/java/dagger",
              "//third_party/java/flogger:android",
              "//third_party/java/jsr330_inject",
    ],
)

arcs_manifest_bundle(
    name = "demo_manifest_bundle",
    deps = [
        "//particles/PipeApps:AndroidAutofill",
        "//particles/PipeApps:RenderNotification",
    ],
)
