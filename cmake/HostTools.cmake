# Host tools may have been built in a different configuration than this build;
# make the imported executable usable in every configuration.
function(use_host_tool_in_all_configs TOOL)
    get_target_property(LOCATION ${TOOL} IMPORTED_LOCATION)
    if(LOCATION)
        return()
    endif()
    get_target_property(CONFIGS ${TOOL} IMPORTED_CONFIGURATIONS)
    list(GET CONFIGS 0 CONFIG)
    get_target_property(LOCATION ${TOOL} IMPORTED_LOCATION_${CONFIG})
    set_target_properties(${TOOL} PROPERTIES IMPORTED_LOCATION ${LOCATION})
endfunction()
