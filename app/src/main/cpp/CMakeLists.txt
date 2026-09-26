cmake_minimum_required(VERSION 3.22)
project(signbridge C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# call_shim.S：精确寄存器调用（x8=out, x1=a, x2=b, w3=c）
enable_language(ASM)
add_library(${CMAKE_PROJECT_NAME} SHARED sign_jni.cpp call_shim.S content_jni.cpp)
target_link_libraries(${CMAKE_PROJECT_NAME} log dl)