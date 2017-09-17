Open xsdb console
```bash
$ xsdb
xsdb% connect
xsdb% targets 2
xsdb% source ps7_init.tcl
xsdb% ps7_init
xsdb% ps7_post_config
xsdb% xsdbserver start
```
On terminal
```bash
$ cd emulator/fpgazynq
$ make dtmxsdb
# sample to show how to use dtmxsdb
# +p is the port number that xsdb will print
# after starting the xsdbserver
$ ./dtmxsdb +verbose +p46677 +loadmem=../../install/riscv-tests/rv32ui-p-add
```
to try the automated tests 
```bash
make -i port=<#xsdbserver> fpga-asm-tests
make -i port=<#xsdbserver> fpga-bmarks-test
make -i port=<#xsdbserver> fpga-run #to run all bmark and asm tests
```
