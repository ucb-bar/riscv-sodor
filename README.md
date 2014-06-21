About The Sodor Processor Collection
====================================

Author : Christopher Celio (celio@eecs.berkeley.edu)

Author : Eric Love

Date   : 2014 May 6
 

This repo has been put together to demonstrate a number of simple [RISC-V](http://riscv.org)
integer pipelines written in [Chisel](http://chisel.eecs.berkeley.edu):

* 1-stage (essentially an ISA simulator)
* 2-stage (demonstrates pipelining in Chisel)
* 3-stage ("Princeton-style", uses sequential memory)
* 5-stage (can toggle between fully bypassed or fully interlocked)
* "bus"-based micro-coded implementation


All of the cores implement the RISC-V 32b integer base user-level ISA (RV32I)
version 2.0.  Only the 1-stage and 3-stage implement supervisor mode.

All processors talk to a simple scratchpad memory (asynchronous,
single-cycle), with no backing outer memory (the 3-stage is the exception
\- its scratchpad is synchronous). Programs are loaded in via a Host-target
Interface (HTIF) port (while the core is kept in reset), effectively making the
scratchpads 3-port memories (instruction, data, HTIF).

This repository is set up to use the C++-backend of Chisel to generate and run
the Sodor emulators.  Users wishing to use the Verilog-backend will need to
write their own testharness and glue code to interface with their own tool
flows.

This repo works great as an undergraduate lab (and has been used by Berkeley's
CS152 class for 3 semesters and counting). See doc/ for an example. 



Getting the repo
================

    $ git clone https://github.com/ucb-bar/riscv-sodor.git


Building the processor emulators
================================

Because this repository is designed to be used as RISC-V processor
examples written in [Chisel](http://chisel.eecs.berkeley.edu) (and a regressive testsuite for Chisel updates),
no external [RISC-V tools](http://riscv.org) are used (with the exception of the RISC-V [front-end server](https://github.com/ucb-bar/riscv-fesvr)). 
The assumption is that [riscv-gcc](https://github.com/ucb-bar/riscv-gcc) is not
available on the local system.  Thus, RISC-V unit tests and benchmarks were
compiled and committed to the sodor repository in the ./install directory (as are the .dump files). 


Install the RISC-V front-end server to talk between the host and RISC-V target processors.

    $ git clone https://github.com/ucb-bar/riscv-fesvr.git
    $ cd riscv-fesvr
    $ ./configure --prefix=/usr/local
    $ make install
 
Build the sodor emulators

    $ git clone https://github.com/ucb-bar/riscv-sodor.git
    $ cd riscv-sodor
    $ ./configure --with-riscv=/usr/local
    $ make

Install the executables on the local system

    $ make install

Clean all generated files

    $ make clean


(Although you can set the prefix to any directory of your choice, they must be
the same directory for both riscv-fesvr and riscv-sodor).

(Alternative) Build together with Chisel sources
------------------------------------------------
 
This repository packages [SBT](http://github.com/harrah/xsbt/wiki/Getting-Started-Setup) 
(Scala Built Tool) for convenience.  By default SBT will fetch the Chisel
package specified in project/build.scala.

If you are a developer on Chisel and are using sodor cores to test your changes
to the Chisel repository, it is convienient to rebuild the Chisel package before
the sodor cores. To do that, fetch the Chisel repo from github and pass the
path to the local Chisel source directory to the configure script.

    $ git clone https://github.com/ucb-bar/chisel.git
    $ cd riscv-sodor
    $ ./configure --with-riscv=/usr/local --with-chisel=../chisel
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
 
When run in debug mode, all processors will generate .vcd information (viewable
by your favorite waveform viewer). **NOTE:** The current build system assumes
that the user has "vcd2vpd" installed.  If not, you will need to make the
appropriate changes to emulator/common/Makefile.include to remove references to
"vcd2vpd".
 
All processors can also spit out cycle-by-cycle log information: see
emulator/common/Makefile.include and add a "+verbose" to the emulator binary
arguments list. **WARNING:** log files may become very large!

By default, assembly tests already use "+verbose" and the longer running
benchmarks do not. See the Makefile rule "run-bmarks:
$(global\_bmarks\_outgz)..." which, if uncommented, will run the benchmarks in
log mode and save the output to a .gz file (you can use "zcat vvadd.out.gz |
vim -" to read these files easily enough, if vim is your thing).

Although already done for you by the build system, to generate .vcd files, see
./Makefile to add the "--vcd" flag to Chisel, and
emulator/common/Makefile.include to add the "-v${vcdfilename}" flag to the
emulator binary. Currently, the .vcd files are converted to .vpd files and then
the .vcd files are deleted. If you do not have vcd2vpd, you will want to remove
references to vcd2vpd in emulator/common/Makefile.include. 

The 1-stage and 3-stage can run the bmarks using the proxy-kernel (pk),
which allows it to trap and emulate illegal instructions (e.g., div/rem), and
allows the use of printf from within the bmark application! (This assumes the
benchmarks have been compiled for use on a proxy kernel. For example, bare
metal programs begin at PC=0x2000, whereas the proxy kernel expects the
benchmark's main to be located at 0x10000. This is controlled by the
tests/riscv-bmarks/Makefile SUPERVISOR\_MODE variable).

Have fun!

TODO
----

Here is an informal list of things that would be nice to get done. Feel free to
contribute!

* Parallelize the compilation of the C++ files (Top-1.cpp, Top-2.cpp, etc.)
* Add stat information back in (e.g., print out the CPI).
* Leverage the uarch-counters (need to update CSRFile).
* Use the newest riscv-test benchmarks, which provide printf (but require
  syscall support) and dump out the uarch counter state.
* Update the 3-stage to RISC-V 2.0.
* Update the 3-stage to work in Princeton mode (previously only worked in 
  Harvard mode with synchronous memory).
* Use the riscv-dis binary to provide diassembly support (instead of using
  Chisel RTL, which is expensive), which is provided by the riscv-fesvr repository.

