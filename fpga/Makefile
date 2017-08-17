include ../prefix.mk


hdl/Top.v: $(srcDir)/src/fpgatop/*.scala $(srcDir)/src/common/*.scala
	cd $(srcDir) && $(SBT) "project fpgatop" "run -td fpga/hdl"
	echo "\`ifndef SYNTHESIS\n\`define SYNTHESIS\n\`endif\n" > hdl/temp
	cat hdl/Top.v >> hdl/temp
	mv hdl/temp hdl/Top.v
	rm hdl/Top.anno hdl/Top.fir

ip_repo: hdl/Top.v
	vivado -mode batch -source package.tcl

bitstream: ip_repo
	vivado -mode batch -source bitstream.tcl
