About The Sodor Processor Collection
====================================
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/librecores/riscv-sodor?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

CI: [Librecores CI](https://ci.librecores.org/job/Projects/job/librecores/job/riscv-sodor/)

Diagrams: [Sodor Github wiki](https://github.com/ucb-bar/riscv-sodor/wiki)

More documentation: [Librecores Sodor wiki](https://github.com/librecores/riscv-sodor/wiki)

Downstream development: [Librecores Sodor](https://github.com/librecores/riscv-sodor)


This repo has been put together to demonstrate a number of simple [RISC-V](http://riscv.org)
integer pipelines written in [Chisel](http://chisel.eecs.berkeley.edu):

* 1-stage (essentially an ISA simulator)
* 2-stage (demonstrates pipelining in Chisel)
* 3-stage (uses sequential memory; supports both Harvard and Princeton versions)
* 5-stage (can toggle between fully bypassed or fully interlocked)
* "bus"-based micro-coded implementation

All of the cores implement the RISC-V 32b integer base user-level ISA (RV32I)
version 2.0. None of the cores support virtual memory, and thus only implement
the Machine-level (M-mode) of the Privileged ISA v1.10 .

All processors talk to a simple scratchpad memory (asynchronous,
single-cycle), with no backing outer memory (the 3-stage is the exception
\- its scratchpad is synchronous). Programs are loaded in via a Debug Transport Module
 (DTM) described in [Debug Spec v0.13](https://static.dev.sifive.com/riscv-debug-spec-0.13.b4f1f43.pdf) while the core is kept in reset.

This repository is set up to use the Verilog file generated by Chisel3 which is fed
to Verilator along with a test harness in C++ to generate and run the Sodor emulators.

See doc/ for microarchitecture diagrams which can be viewed using [draw.io](https://www.draw.io) using the following example link 
https://www.draw.io/?url=https://raw.githubusercontent.com/librecores/riscv-sodor/master/doc/1stage.xml wherein master/doc/1stage.xml needs to be changed as needed

Directory Structure
===================

doc - Microarchitecture diagrams for all stages in XML format to be used with draw.io

emulator - C source used as test harness are fed to verilator to generate emulator

install - Compiled binaries of ISA/BENCHMARK tests

project - Scala configuration files fed to Scala Build Tool(sbt)

riscv-fesvr - Frontend Server for the target to load the binaries and execute any requested syscall. It is a forked version to add support for system-bus access

riscv-tests - Recipe to generate ISA/BENCHMARK tests

sbt - sbt_launch.jar which is fed to java to launch sbt

src - Scala Sources

vsrc - Verilog Sources used for blackbox in chisel

Makefile - To automate building the emulators

Getting the repo
================
```bash
git clone --recursive https://github.com/librecores/riscv-sodor.git
cd riscv-sodor
```

Building the processor emulators
================================

Because this repository is designed to be used as RISC-V processor
examples written in [Chisel3](https://github.com/freechipsproject/chisel3/wiki) (and a regressive testsuite for Chisel updates),
no external [RISC-V tools](http://riscv.org) are used (with the exception of
the RISC-V [front-end server](https://github.com/codelec/riscv-fesvr) and
optionally, the [spike-dasm](https://github.com/riscv/riscv-isa-run) binary to
provide a disassembly of instructions in the generated *.out files).
The assumption is that [riscv-gnu-toolchain](https://github.com/riscv/riscv-gnu-toolchain) is not
available on the local system.  Thus, RISC-V unit tests and benchmarks were
compiled and committed to the sodor repository in the ./install directory (as are the .dump files).

Install verilator using any of the following possible ways
For Ubuntu 17.04
```bash
sudo apt install pkg-config verilator
#optionally gtkwave to view waveform dumps
```

For Ubuntu 16.10 and lower
```bash 
sudo apt install pkg-config
wget http://mirrors.kernel.org/ubuntu/pool/universe/v/verilator/verilator_3.900-1_amd64.deb
sudo dpkg -i verilator_3.900-1_amd64.deb
```

If you don't have enough permissions to use apt on your machine
```bash
# make autoconf g++ flex bison should be available
wget https://www.veripool.org/ftp/verilator-3.906.tgz
tar -xzf verilator-3.906.tgz
cd verilator-3.906
unset VERILATOR_ROOT
./configure
make
export VERILATOR_ROOT=$PWD
export PATH=$PATH:$VERILATOR_ROOT/bin
```

Install the RISC-V front-end server to talk between the host and RISC-V target processors.
```bash
cd riscv-fesvr
mkdir build; cd build
../configure --prefix=/usr/local
make install 
```

Build the sodor emulators
```bash
make
# To run the all the stages with the given tests available in ./install
make run-emulator
# Clean all generated files
make clean
```

Running the RISC-V tests
========================

    $ make run-emulator

(Optional) Running debug version to produce signal traces
---------------------------------------------------------
```bash
make run-emulator-debug
```
When run in debug mode, all processors will generate .vcd information (viewable
by your favorite waveform viewer). All processors can also spit out cycle-by-cycle 
log information.
Although already done for you by the build system, to generate .vcd files, see
 emulator/common/Makefile.include to add the "-v${vcdfilename}" flag to the
emulator-debug binary.

RISC-V fesvr allows you to use elf as input to sodor cores so no need to generate
the hex files

Have fun!

The riscv-test Collection
=========================

Sodor includes a submodule link to the "riscv-tests" repository. To help Sodor
users, the tests and benchmarks have been pre-compiled and placed in the
./install directory.

Building RISC-V Toolchain
--------------------------

If you would like to compile your own tests, you will need to build an
RISC-V compiler. Set $RISCV to where you would like to install RISC-V related
tools generally `/opt/riscv`, and make sure that $RISCV/bin is in your path.
```bash
git clone --recursive https://github.com/riscv/riscv-gnu-toolchain.git
cd riscv-gnu-toolchain
mkdir build; cd build
../configure --prefix=$RISCV --enable-multilib
make -j4
```
This will install a compiler named riscv64-unknown-elf-gcc

### Alternative 
Sifive provides prebuilt toolchain found here https://www.sifive.com/products/tools/ which can be used to generate ELF's for Sodor
```bash
#Before dowloading(~326MB) the archive do check if 20171231 is latest available on their website
wget https://static.dev.sifive.com/dev-tools/riscv64-unknown-elf-gcc-20171231-x86_64-linux-centos6.tar.gz
tar -xzf riscv64-unknown-elf-gcc-20171231-x86_64-linux-centos6.tar.gz -C /opt
rm riscv64-unknown-elf-gcc-20171231-x86_64-linux-centos6.tar.gz
mv /opt/riscv64-unknown-elf-gcc-20171231-x86_64-linux-centos6 /opt/riscv
export PATH=/opt/riscv/bin:$PATH
export RISCV=/opt/riscv
```

Compiling the tests
----------------------------
Append to line in [isa/Makefile:33](https://github.com/riscv/riscv-tests/blob/6f7ebb610d6bb8817a9592cc06a7d108381f1761/isa/Makefile#L33)  `-march=rv32i -mabi=ilp32`
```bash
    cd riscv-tests/isa
    make rv32ui
    make rv32mi
```
Sodor only supports the rv32ui-p (user-level) and rv32mi-p (machine-level) physical memory tests.

Append to line in [benchmarks/Makefile:40](https://github.com/riscv/riscv-tests/blob/6f7ebb610d6bb8817a9592cc06a7d108381f1761/benchmarks/Makefile#L40)  `-march=rv32i -mabi=ilp32`
```bash
    cd riscv-tests/benchmarks
    make #will fail at compiling mm which is not supported and not needed
    make dhrystone.riscv
```
After compiling the tests and benchmarks, for the tests edit line in [emulator/common/Makefile.include:138](https://github.com/librecores/riscv-sodor/blob/92663cc23f0d52d20e448802c4c5def8a717fa1c/emulator/common/Makefile.include#L138) to indicate the appropriate path to ELF's and similarly for benchmarks by editing [emulator/common/Makefile.include:191](https://github.com/librecores/riscv-sodor/blob/92663cc23f0d52d20e448802c4c5def8a717fa1c/emulator/common/Makefile.include#L191)

Running tests on the ISA simulator
----------------------------------

If you would like to run tests yourself, you can use the Spike ISA simulator
(found in riscv-tools on the riscv.org webpage). By default, Spike executes in
RV64G mode. To execute RV32I binaries, for example:

    cd ./install
    spike --isa=RV32I rv32ui-p-simple
    spike --isa=RV32I dhrystone.riscv

The generated assembly code looks too complex!
----------------------------------------------

For Sodor, the assembly tests rely on macros that can be found in the
riscv-tests/env/p directory. You can simplify these macros as desired.

Unittests
----------

Unittests are added to `src/test` directory. Currently tests are for Debug and ScratchpadMemory only
```bash 
  $ sbt "project common" shell
  > testOnly tests.MemoryTester
  > testOnly tests.DebugTests
``` 
or 
```bash 
  $ make MK_TARGET_PROC=common shell
  > testOnly tests.MemoryTester
  > testOnly tests.DebugTests
```
Inorder to write unittests for modules from other projects(eg. rv32_1stage,rv32_ucode) [build.scala](project/build.scala) needs to be modified appropriately

FAQ
===

*What is the goal of these cores?*

First and foremost, to provide a set of easy to understand cores that users can
easily modify and play with. Sodor is useful both as a quick introduction to
the [RISC-V ISA](http://riscv.org) and to the hardware construction language
[Chisel3](http://chisel.eecs.berkeley.edu).

*Are there any diagrams of these cores?*

Diagrams of some of the processors can be found either in the
[Sodor Github wiki](https://github.com/ucb-bar/riscv-sodor/wiki), in doc/,
or in doc/lab1.pdf.  A more comprehensive write-up on the micro-code implementation can
be found at the [CS152 website](http://inst.eecs.berkeley.edu/~cs152/sp12/handouts/microcode.pdf).


*How do I generate Verilog code for use on a FPGA?*

Chisel3 outputs verilog by default which can be generated by
```bash
cd emulator/rv32_1stage
make generated-src/Top.v
```

*I want to help! Where do I go?*

You can participate in the Sodor conversation on [gitter](https://gitter.im/librecores/riscv-sodor). Downstream development is also taking place at [Librecores](https://github.com/librecores/riscv-sodor). Major milestones will be pulled back here. Check it out! We also accept pull requests here!

TODO
----

Here is an informal list of things that would be nice to get done. Feel free to
contribute!

* Reduce the port count on the scratchpad memory by having the Debug Module port
  share one of the cpu ports.
* Add support for the ma_addr, ma_fetch ISA tests. This requires detecting
  misaligned address exceptions.
* Greatly cleanup the common/csr.scala file, to make it clearer and more
  understandable.
* Refactor the stall, kill, fencei, and exception logic of the 5-stage to be
  more understandable.
