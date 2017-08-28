set root "."
set design sodor_temp
set partname "xc7z020clg400-1"
set projdir ./ip_repo/sodor_temp_1.0
set hdl_files [glob $root/hdl/*]
puts $hdl_files
create_project -force $design $projdir -part $partname
set_property target_language Verilog [current_project]
# create_peripheral user.org user sodor_temp 1.0 -dir $root/ip_repo
# add_peripheral_interface S00_AXI -interface_mode slave -axi_type full [ipx::find_open_core user.org:user:sodor_temp:1.0]
# add_peripheral_interface M00_AXI -interface_mode master -axi_type full [ipx::find_open_core user.org:user:sodor_temp:1.0]
# generate_peripheral -force [ipx::find_open_core user.org:user:sodor_temp:1.0]
# write_peripheral [ipx::find_open_core user.org:user:sodor_temp:1.0]
# set_property  ip_repo_paths  $root/ip_repo/sodor_temp_1.0 [current_project]
# update_ip_catalog -rebuild
# ipx::edit_ip_in_project -upgrade true -directory $root/ip_repo $root/ip_repo/sodor_temp_1.0/component.xml
# remove_files  $root/ip_repo/sodor_temp_1.0/hdl/sodor_temp_v1_0_S00_AXI.v
# remove_files  $root/ip_repo/sodor_temp_1.0/hdl/sodor_temp_v1_0_M00_AXI.v
# add_files -norecurse -copy_to $root/ip_repo/sodor_temp_1.0/src /home/kritik/github/riscv-sodor/Top.v
# update_compile_order -fileset sources_1
# update_files -from_files /home/kritik/pynq/sodor/fpga/sodor_temp_v1_0.v -to_files $root/ip_repo/sodor_temp_1.0/hdl/sodor_temp_v1_0.v -filesets [get_filesets *]
# update_compile_order -fileset sources_1
# ipx::merge_project_changes files [ipx::current_core]
# ipx::merge_project_changes hdl_parameters [ipx::current_core]
# set_property core_revision 2 [ipx::current_core]
# ipx::create_xgui_files [ipx::current_core]
# ipx::update_checksums [ipx::current_core]
# ipx::save_core [ipx::current_core]
# update_ip_catalog -rebuild
# #close_project -delete

if {[string equal [get_filesets -quiet sources_1] ""]} {
    create_fileset -srcset sources_1
}

add_files -force -verbose -norecurse -copy_to $projdir/src -fileset [get_filesets sources_1] $hdl_files

set_property top sodor_zynq_v1_0 [get_filesets sources_1]

ipx::package_project -import_files -force -root_dir $projdir
ipx::associate_bus_interfaces -busif s00_axi -clock s00_axi_aclk [ipx::current_core]
ipx::associate_bus_interfaces -busif m00_axi -clock m00_axi_aclk [ipx::current_core]

set_property vendor              {user.org}    [ipx::current_core]
set_property library             {user}                  [ipx::current_core]
set_property name             {sodor_temp}                  [ipx::current_core]
set_property taxonomy            {{/AXI_Infrastructure}} [ipx::current_core]
set_property vendor_display_name {user}              [ipx::current_core]
set_property supported_families  { \
                     {artix7}     {Production} \
                     {artix7l}    {Production} \
                     {aartix7}    {Production} \
                     {qartix7}    {Production} \
                     {zynq}       {Production} \
                     {qzynq}      {Production} \
                     {azynq}      {Production} \
                     }   [ipx::current_core]

############################
# Save and Write ZIP archive
############################

ipx::create_xgui_files [ipx::current_core]
ipx::update_checksums [ipx::current_core]
ipx::save_core [ipx::current_core]
close_project
