srcDir          := $(abspath ..)
SBT := java -Xmx4096M -Xss8M -XX:MaxPermSize=128M -jar $(srcDir)/sbt/sbt-launch.jar $(SBT_FLAGS)

hdl/Top.v: $(srcDir)/src/fpgatop/$(wildcard *.scala) $(srcDir)/src/common/$(wildcard *.scala)
	cd $(srcDir) && $(SBT) "project fpgatop" "run -td fpga/hdl"
	echo "\`ifndef SYNTHESIS\n\`define SYNTHESIS\n\`endif\n" > hdl/temp
	cat hdl/Top.v >> hdl/temp
	mv hdl/temp hdl/Top.v
	rm hdl/Top.anno hdl/Top.fir

ip_repo: hdl/Top.v
	vivado -mode batch -source package.tcl

bitstream: ip_repo
	vivado -mode batch -source bitstream.tcl
