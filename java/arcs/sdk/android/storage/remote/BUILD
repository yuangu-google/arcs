load("//third_party/java/arcs/build_defs:build_defs.bzl", "arcs_kt_android_library")
load(
    "//third_party/java/arcs/build_defs:native.oss.bzl",
    "android_proto_library",
    "proto_library",
)

licenses(["notice"])

package(default_visibility = ["//java/arcs:allowed-packages"])

proto_library(
    name = "remote_message_proto",
    srcs = ["remote_message.proto"],
    deps = [
        "//java/arcs/android/storage:proxy_message_proto",
        "//java/arcs/android/storage:store_options_proto",
        "//third_party/java/arcs/deps:protobuf_wrappers_proto",
    ],
)

android_proto_library(
    name = "remote_message_android_proto",
    deps = [":remote_message_proto"],
)


arcs_kt_android_library(
    name = "remote",
    srcs = glob(["*.kt"]),
    manifest = "//java/arcs/android/common:AndroidManifest.xml",
    deps = [
        ":remote_message_android_proto",
        "//java/arcs/android/common/resurrection",
        "//java/arcs/android/crdt",
        "//java/arcs/android/storage",
        "//java/arcs/android/storage:proxy_message_android_proto",
        "//java/arcs/android/storage/database",
        "//java/arcs/android/storage/service",
        "//java/arcs/android/storage/service:aidl",
        "//java/arcs/core/common",
        "//java/arcs/core/crdt",
        "//java/arcs/core/data",
        "//java/arcs/core/storage",
        "//java/arcs/core/storage/api",
        "//java/arcs/core/util",
        "//java/arcs/sdk/android/storage/service",
        "//third_party/java/androidx/annotation",
        "//third_party/kotlin/kotlinx_atomicfu",
        "//third_party/kotlin/kotlinx_coroutines",
    ],
)
