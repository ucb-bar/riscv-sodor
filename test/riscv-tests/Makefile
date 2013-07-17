#=======================================================================
# UCB VLSI FLOW: Makefile for local riscv-tests 
#-----------------------------------------------------------------------
# Yunsup Lee (yunsup@cs.berkeley.edu)
#

default: all
  
instname    = riscv-tests
instbasedir = ../../install
                                 
#--------------------------------------------------------------------
# Sources
#--------------------------------------------------------------------

# turn off ability to compile and run tests,
# since I'm assuming the user doesn't have 
# the full tool-chain installed.
asm_tests = \
	riscv_example.S \
	riscv-v1_simple.S \
	riscv-v1_addi.S \
	riscv-v1_bne.S \
	riscv-v1_sw.S \
	riscv-v2_addi.S \
	riscv-v2_add.S \
	riscv-v2_and.S \
	riscv-v2_andi.S \
	riscv-v2_beq.S \
	riscv-v2_bge.S \
	riscv-v2_bgeu.S \
	riscv-v2_blt.S \
	riscv-v2_bltu.S \
	riscv-v2_bne.S \
	riscv-v2_j.S \
	riscv-v2_jal.S \
	riscv-v2_jalr.S  \
	riscv-v2_jalr_j.S \
	riscv-v2_jalr_r.S \
	riscv-v2_lui.S \
	riscv-v2_lw.S \
	riscv-v2_or.S \
	riscv-v2_ori.S \
	riscv-v2_simple.S \
	riscv-v2_slt.S \
	riscv-v2_sltu.S \
	riscv-v2_slti.S \
	riscv-v2_sltiu.S \
	riscv-v2_slli.S \
	riscv-v2_sll.S \
	riscv-v2_srli.S \
	riscv-v2_srl.S \
	riscv-v2_sra.S \
	riscv-v2_srai.S \
	riscv-v2_sub.S \
	riscv-v2_sw.S \
	riscv-v2_xor.S \
	riscv-v2_xori.S \
	riscv-v2_auipc.S \
	riscv-v3_lb.S \
	riscv-v3_lbu.S \
	riscv-v3_lh.S \
	riscv-v3_lhu.S \
	riscv-v3_sb.S \
	riscv-v3_sh.S \
	riscv-vu_addm.S \
	riscv-vu_movn.S \
	riscv-vu_strcpy.S \
#	riscv-v1_lw.S \  #won't pass because can't fit program into small memory footprint if we start at 0x2000

extra_files = \
	test_macros.h \

#--------------------------------------------------------------------
# Build rules
#--------------------------------------------------------------------

RISCV_GCC = riscv-gcc -m32
RISCV_GCC_OPTS = -O2 -nostdlib -nostartfiles -T test.ld
RISCV_OBJDUMP = riscv-objdump --disassemble-all --disassemble-zeroes --section=.text --section=.data
RISCV_SIM = echo r | spike -d #sorry, this is currently broken; look at the ISA simulator "spike" to find the correct arguments

#------------------------------------------------------------
# Build assembly tests

asm_tests_bin  = $(patsubst %.S, %, $(asm_tests))
asm_tests_dump = $(addsuffix .dump, $(asm_tests_bin))
asm_tests_hex = $(addsuffix .hex, $(asm_tests_bin))
asm_tests_out  = $(addsuffix .out,  $(asm_tests_bin))
 
%.hex: %
	elf2hex 16 16384 $< > $@
 
$(asm_tests_dump): %.dump: %
	$(RISCV_OBJDUMP) $< > $@

$(asm_tests_bin): %: %.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) $< -o $@

$(asm_tests_out): %.out: %
	$(RISCV_SIM) $< > $@

riscv-: $(asm_tests_dump) $(asm_tests_hex)
run: $(asm_tests_out)
	echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if /\*{3}(.{8})\*{3}(.*)/' \
	       $(asm_tests_out); echo;

junk += $(asm_tests_bin) $(asm_tests_dump) $(asm_tests_out) $(asm_tests_hex)

#------------------------------------------------------------
# Default

all: riscv-
    
#------------------------------------------------------------
# Install
                               
install_dir = $(instbasedir)/$(instname)

install:
	mkdir -p $(install_dir)
	cp -r $(asm_tests_bin) $(asm_tests_dump) $(asm_tests_hex) $(install_dir)

    
#------------------------------------------------------------
# Clean up

clean:
	rm -rf $(junk)
