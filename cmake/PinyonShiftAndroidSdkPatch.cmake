# Keep the upstream SDK pin reproducible: the Android changes live in this
# repository as a reviewed patch, rather than an unreachable submodule commit.
find_package(Git REQUIRED)
file(GLOB _fh4droid_sdk_patches CONFIGURE_DEPENDS
    "${PROJECT_SOURCE_DIR}/cmake/patches/shiftglue-fh4droid-*.patch")
foreach(_fh4droid_sdk_patch IN LISTS _fh4droid_sdk_patches)
execute_process(
    COMMAND "${GIT_EXECUTABLE}" -C "${REXSDK_DIR}" apply --reverse --check
        "${_fh4droid_sdk_patch}"
    RESULT_VARIABLE _fh4droid_patch_applied OUTPUT_QUIET ERROR_QUIET)
if(NOT _fh4droid_patch_applied EQUAL 0)
    execute_process(
        COMMAND "${GIT_EXECUTABLE}" -C "${REXSDK_DIR}" apply --check
            "${_fh4droid_sdk_patch}"
        RESULT_VARIABLE _fh4droid_patch_clean OUTPUT_QUIET ERROR_VARIABLE _fh4droid_patch_error)
    if(NOT _fh4droid_patch_clean EQUAL 0)
        message(FATAL_ERROR
            "The FH4Droid SDK patch does not match this checkout. Use the pinned "
            "ShiftGlue SDK or resolve local SDK changes first.\n${_fh4droid_patch_error}")
    endif()
    execute_process(
        COMMAND "${GIT_EXECUTABLE}" -C "${REXSDK_DIR}" apply "${_fh4droid_sdk_patch}"
        RESULT_VARIABLE _fh4droid_patch_result)
    if(NOT _fh4droid_patch_result EQUAL 0)
        message(FATAL_ERROR "Could not apply the FH4Droid SDK patch")
    endif()
endif()

endforeach()
