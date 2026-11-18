# A linked facade library is insufficient: the entrypoint must register it
# before the guest loads the XEX. Check frozen trees as well as fresh codegen.
function(pinyon_shift_verify_generated_modules generated_root generated_main)
    set(_main "${generated_main}")
    foreach(_file IN ITEMS pinyon_shift_init.cpp module_registry.cpp sources.cmake)
        if(NOT EXISTS "${_main}/${_file}")
            message(FATAL_ERROR "Incomplete generated module registry: ${_main}/${_file}. Generate the complete project manifest, including SpeechFacade and XMediaFacade.")
        endif()
    endforeach()
    file(READ "${_main}/pinyon_shift_init.cpp" _init)
    if(NOT _init MATCHES "[.]register_modules[ \t\r\n]*=[ \t\r\n]*RegisterRecompiledModules")
        message(FATAL_ERROR "The generated entrypoint does not connect RegisterRecompiledModules. A main-only codegen tree cannot start the game.")
    endif()
    file(READ "${_main}/sources.cmake" _sources)
    if(NOT _sources MATCHES "module_registry[.]cpp")
        message(FATAL_ERROR "The generated build omits module_registry.cpp.")
    endif()
    file(READ "${_main}/module_registry.cpp" _registry)
    string(TOLOWER "${_registry}" _registry)
    foreach(_module IN ITEMS speech xmedia)
        if(NOT _registry MATCHES "${_module}facade_default[.]xex" OR
           NOT _registry MATCHES "pinyon_shift_${_module}facade_default")
            message(FATAL_ERROR "The generated registry omits the ${_module} facade.")
        endif()
    endforeach()
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
        "${_main}/pinyon_shift_init.cpp" "${_main}/module_registry.cpp"
        "${_main}/sources.cmake")
endfunction()
