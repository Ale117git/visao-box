cmake_minimum_required(VERSION 3.18.1)
project(vrcore CXX)

add_library(vrcore SHARED vrcore.cpp)
target_compile_features(vrcore PRIVATE cxx_std_17)
target_compile_options(vrcore PRIVATE -O3 -ffast-math -fvisibility=hidden -Wall)
target_link_libraries(vrcore GLESv2 android log jnigraphics)
