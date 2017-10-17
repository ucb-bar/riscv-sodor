include ../../prefix.mk

MK_TARGET_PROC  ?= zynqsimtop
SUPERVISOR_MODE="false"

bmark_timeout ?=100000000

global_asm_tests += \
	rv32mi-p-csr \
	rv32mi-p-illegal \


include $(srcDir)/emulator/common/Makefile.include

