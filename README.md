About The Sodor Processor Collection
====================================

Author : Christopher Celio (celio@eecs.berkeley.edu)

Date   : 2013 July 16

This repo has been put together to demonstrate a number of simple [RISC-V](http://riscv.org)
integer pipelines:

* 1-stage (essentially an ISA simulator)
* 2-stage (demonstrates pipelining in Chisel)
* 3-stage ("Princeton-style" memory, optimized for area)
* 5-stage (can toggle between fully bypassed or fully interlocked)
* "bus"-based micro-coded implementation

All of the cores implement a simple subset of the RISC-V 32b integer base
user-level ISA (RV32I).  The cores do not implement floating point, and most do
not implement sub-word memory accesses nor supervisor mode (the 1-stage and
3-stage being the exceptions).

All processors talk to a simple scratchpad memory (asynchronous,
single-cycle), with no backing outer memory. Programs are loaded in via a
Host-target Interface (HTIF) port (while the core is kept in reset),
effectively making the scratchpads 3-port memories (instruction, data, HTIF).

This repository is set up to use the C++-backend of Chisel to generate and run
the Sodor emulators.  Users wishing to use the Verilog-backend will need to
write their own testharness and glue code to interface with their own tool
flows.

This repo works great as an undergraduate lab.



Getting the repo
================

    $ git clone https://github.com/ucb-bar/riscv-sodor.git


Building the processor emulators
================================

Because this repository is designed to be used as RISC-V processor
examples written in Chisel (and a regressive testsuite for Chisel updates),
No external [RISC-V tools](http://riscv.org) are used. The assumption is that
[riscv-gcc](https://github.com/ucb-bar/riscv-gcc) is not available
on the local system.

RISC-V unit tests and benchmarks were compiled and committed into the sodor
repositories. The only prerequisites are thus those to build the core
emulators themselves.


Install the RISC-V front-end server to talk between the host and RISC-V target processors.

    $ git clone https://github.com/ucb-bar/riscv-fesvr.git
    $ cd riscv-fesvr
    $ ./configure --prefix=/usr/local
    $ make install

This repository packages [SBT](http://github.com/harrah/xsbt/wiki/Getting-Started-Setup)
(Scala Built Tool) for convenience. You may find it necessary to increase
the memory size (on the java sbt-launch.jar command line) from 512M to 2G.

Build the sodor emulators

    $ git clone https://github.com/ucb-bar/riscv-sodor.git
    $ cd riscv-sodor
    $ ./configure --prefix=/usr/local
    $ make

Install the executables on the local system

    $ make install

Clean all generated files

    $ make clean


(Alternative) Build together with Chisel sources
------------------------------------------------

By default sbt will fetch the Chisel package specified in project/build.scala.

If you are a developer on Chisel and are using sodor cores to test your changes
to the Chisel repo, it is convienient to rebuild the Chisel package before
the sodor cores. To do that, fetch the Chisel repo from github and pass the
path to the local Chisel source directory to the configure script.

    $ git clone https://github.com/ucb-bar/chisel.git
    $ cd riscv-sodor
    $ ./configure --prefix=/usr/local --with-chisel=../chisel
    $ make

Creating a source release package
=================================

    $ make dist-src


Running the RISC-V tests
========================

    $ make run-emulator

Gathering the results
---------------------

    (all)   $ make reports
    (cpi)   $ make reports-cpi
    (bp)    $ make reports-bp
    (stats) $ make reports-stats

(Optional) Running debug version to produce signal traces
---------------------------------------------------------

    $ make run-emulator-debug


All processors can spit out cycle-by-cycle log information: see
emulator/common/Makefile.include and add a "+verbose" to the fesvr binary
arguments list (front-end server). WARNING: log files may become very large!
By default, assembly tests already use "+verbose" and the longer running
benchmarks do not. See the rule "run-bmarks: $(global\_bmarks\_outgz)..." which,
if uncommented, will run the benchmarks in log mode and save the output to a
.gz file (you can use "zcat vvadd.out.gz | vim -" to read these files
easily enough, if vim is your thing).

All processors can also spit out .vcd information (viewable by your favorite
waveform viewer). See ./Makefile to add the "--vcd" flag to Chisel, and
emulator/common/Makefile.include to add the "-v${vcdfilename}" flag to the
fesvr binary. You should see example lines using these flags commented out.
By default, the assembly tests write to a file called cpu.vcd.

The 1-stage and 3-stage can run the bmarks using the proxy-kernel (pk),
which allows it to trap and emulate illegal instructions (e.g., div/rem), and
allows the use of printf from within the bmark application! (This assumes the
benchmarks have been compiled for use on a proxy kernel. For example, bare
metal programs begin at PC=0x2000, whereas the proxy kernel expects main to be
located at 0x10000. This is controlled by the tests/riscv-bmarks/Makefile
SUPERVISOR\_MODE variable).

Have fun!


