RISCV           := /opt/riscv
srcDir          := $(abspath ../../)
buildIncludeDir := $(RISCV)/include
buildLibDir     := $(RISCV)/lib
buildDir        := $(abspath ../../)

# Paths to different source trees
chiseldir       := 

CXX := g++
SBT := java -Xmx4096M -Xss8M -XX:MaxPermSize=128M -jar $(srcDir)/sbt/sbt-launch.jar $(SBT_FLAGS)

global_asm_tests += \
	rv32mi-p-csr \
	rv32mi-p-illegal \


include $(srcDir)/emulator/common/Makefile.include

