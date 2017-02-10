About The Sodor Processor Collection
====================================

Author  : Christopher Celio (celio@eecs.berkeley.edu)

Author  : Eric Love

Date    : 2014 May 6

Diagrams: [Sodor Github wiki](https://github.com/ucb-bar/riscv-sodor/wiki)


This repo has been put together to demonstrate a number of simple [RISC-V](http://riscv.org)
integer pipelines written in [Chisel](http://chisel.eecs.berkeley.edu):

* 1-stage (essentially an ISA simulator)
* 2-stage (demonstrates pipelining in Chisel)
* 3-stage (uses sequential memory; supports both Harvard and Princeton versions)
* 5-stage (can toggle between fully bypassed or fully interlocked)
* "bus"-based micro-coded implementation

All of the cores implement the RISC-V 32b integer base user-level ISA (RV32I)
version 2.0. None of the cores support virtual memory, and thus only implement
the Machine-level (M-mode) of the Privileged ISA v1.7 (Mbare).

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
CS152 class for 3 semesters and counting). See doc/ for an example, as well as
for some processor diagrams. Be careful though - admittedly some of those
documents may become dated as things like the Privileged ISA evolve.



Getting the repo
================

    $ git clone https://github.com/ucb-bar/riscv-sodor.git
    $ cd riscv-sodor
    $ git submodule update --init --recursive


Building the processor emulators
================================

Because this repository is designed to be used as RISC-V processor
examples written in [Chisel](http://chisel.eecs.berkeley.edu) (and a regressive testsuite for Chisel updates),
no external [RISC-V tools](http://riscv.org) are used (with the exception of
the RISC-V [front-end server](https://github.com/riscv/riscv-fesvr) and
optionally, the [spike-dasm](https://github.com/riscv/riscv-isa-run) binary to
provide a disassembly of instructions in the generated *.out files).
The assumption is that [riscv-gnu-toolchain](https://github.com/riscv/riscv-gnu-toolchain) is not
available on the local system.  Thus, RISC-V unit tests and benchmarks were
compiled and committed to the sodor repository in the ./install directory (as are the .dump files).


Install the RISC-V front-end server to talk between the host and RISC-V target processors.

    $ cd riscv-fesvr
    $ ./configure --prefix=/usr/local
    $ make install

Build the sodor emulators

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

If you are a developer of Chisel and are using sodor cores to test your changes
to the Chisel repository, it is convenient to rebuild the Chisel package before
building the sodor cores. To do that, fetch the Chisel repo from github and pass 
the path to the local Chisel source directory to the configure script.

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
metal programs begin at PC=0x200, whereas the proxy kernel expects the
benchmark's main to be located at 0x10000. This is controlled by the
tests/riscv-bmarks/Makefile SUPERVISOR\_MODE variable).

Have fun!

The riscv-test Collection
=========================

Sodor includes a submodule link to the "riscv-tests" repository. To help Sodor
users, the tests and benchmarks have been pre-compiled and placed in the
./install directory.

Building a RV32I Toolchain
--------------------------

If you would like to compile your own tests, you will need to build an
RV32I compiler. Set $RISCV to where you would like to install RISC-V related
tools, and make sure that $RISCV/bin is in your path.

    $ git clone git@github.com:riscv/riscv-gnu-toolchain.git
    $ cd riscv-gnu-toolchain
    $ mkdir build; cd build
    $ ../configure --prefix=$RISCV --disable-float --disable-atomic --with-xlen=32 --with-arch=I
    $ make install

This will install a compiler named riscv32-unknown-elf-gcc, complete with
newlib libraries that will only emit integer instructions. More advanced users
will want to consult the riscv-gnu-toolchain README regarding multilib support
for different base ISAs.

Compiling the tests yourself
----------------------------

    cd riscv-tests/isa
    make

This will compile ALL RISC-V assembly tests (32b and 64b). Sodor only supports
the rv32ui-p (user-level) and rv32mi-p (machine-level) physical memory tests.


    cd riscv-tests/benchmarks
    make

You will need to modify the Makefile in riscv-tests/benchmarks to compile RV32I
binaries.  By default, it will compile RV64G. If you compiled a pure RV32I
compiler, then you may only need to change the name of the compiler used
(riscv32-unknown-elf-gcc).  If your toolchain supports multiple ISAs, then you
may need to specify "-m32 --with-arch=RV32I" for the compiler and linker flags
as appropriate.

Running tests on the ISA simulator
----------------------------------

If you would like to run tests yourself, you can use the Spike ISA simulator
(found in riscv-tools on the riscv.org webpage). By default, Spike executes in
RV64G mode. To execute RV32I binaries, for example:

    cd ./install
    spike --ISA=RV32I rv32ui-p-simple
    spike --ISA=RV32I dhrystone.riscv

The generated assembly code looks too complex!
----------------------------------------------

For Sodor, the assembly tests rely on macros that can be found in the
riscv-tests/env/p directory. You can simplify these macros as desired.


FAQ
===

*What is the goal of these cores?*

First and foremost, to provide a set of easy to understand cores that users can
easily modify and play with. Sodor is useful both as a quick introduction to
the [RISC-V ISA](http://riscv.org) and to the hardware construction language
[Chisel](http://chisel.eecs.berkeley.edu).

*Are there any diagrams of these cores?*

Diagrams of some of the processors can be found either in the
[Sodor Github wiki](https://github.com/ucb-bar/riscv-sodor/wiki), in doc/,
or in doc/lab1.pdf.  A more comprehensive write-up on the micro-code implementation can
be found at the [CS152 website](http://inst.eecs.berkeley.edu/~cs152/sp12/handouts/microcode.pdf).


*How do I generate Verilog code for use on a FPGA?*

The Sodor repository is set up to use the C++-backend of Chisel to generate and
run the Sodor emulators. Users wishing to use the Verilog-backend will
unfortunately need to write their own testharness and glue code to interface
with their own tool flows.

*Why no Verilog?*

In a past iteration, Sodor has used Synopsys's VCS and DirectC to provide a
Verilog flow for Verlog RTL simulation.  However, as VCS/DirectC is not freely
available, it was not clear that committing Verilog code dependent on a
proprietary simulation program was a good idea.


*How can I generate Verilog myself?*

You can generate the Verilog code by modifying the Makefile in
emulator/common/Makefile.include.  In the CHISEL_ARGS variable, change
"--backend c" to "--backend v". This will dump a Top.v verilog file of the core
and its scratchpad memory (corresponding to the Chisel module named "Top") into
the location specified by "--targetDir" in CHISEL_ARGS.

Once you have the Top.v module, you will have to write your own testharness and
glue code to talk to Top.v.  The main difficulty here is that you need to link
the riscv-fesvr to the Sodor core via the HTIF link ("host-target interface").
This allows the fesvr to load a binary into the Sodor core's scratchpad memory,
bring the core out of reset, and communicate with the core while it's running
to handle any syscalls, error conditions, or test successful/end conditions.

This basically involves porting emulator/\*/emulator.cpp to Verilog.  I
recommend writing a Verilog testharness that interfaces with the existing C++
code (emulator/common/htif_emulator.cc, etc.).  emulator/common/htif_main.cc
shows an example stub that uses Synopsys's DirectC to interface between a
Verilog test-harness and the existing C++ code.



TODO
----

Here is an informal list of things that would be nice to get done. Feel free to
contribute!

* Update to the Privileged Spec v1.9
* Update to Chisel3/FIRRTL
* Reduce the port count on the scratchpad memory by having the HTIF port
  share one of the cpu ports.
* Provide a Verilog test harness, and put the 3-stage on a FPGA.
* Add support for the ma_addr, ma_fetch ISA tests. This requires detecting
  misaligned address exceptions.
* Greatly cleanup the common/csr.scala file, to make it clearer and more
  understandable.
* Refactor the stall, kill, fencei, and exception logic of the 5-stage to be
  more understandable.
* Update the u-code to properly handle illegal instructions (rv32mi-p-illegal)
  and to properly handle exceptions generated by the CSR file (rv32mi-p-csr).

