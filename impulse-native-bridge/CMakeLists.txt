if(NOT CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    message(FATAL_ERROR "This project requires GCC or Clang to compile.")
endif()