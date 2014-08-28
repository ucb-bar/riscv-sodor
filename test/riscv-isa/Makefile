#=======================================================================
# Makefile for riscv-tests/isa
#-----------------------------------------------------------------------

isa_src_dir := .
install_dir := $(isa_src_dir)/../../install/riscv-isa

default: all

tests := \
	simple \
	add addi \
	and andi \
	auipc \
	beq bge bgeu blt bltu bne \
	fence_i \
	j jal jalr \
	lb lbu lh lhu lw \
	lui \
	or ori \
	sb sh sw \
	sll slli \
	slt slti \
	sra srai \
	srl srli \
	sub \
	xor xori \

#--------------------------------------------------------------------
# Build rules
#--------------------------------------------------------------------

RISCV_GCC = riscv-gcc
RISCV_GCC_OPTS = -nostdlib -nostartfiles -Wa,-march=RVIMAFDXhwacha
RISCV_OBJDUMP = riscv-objdump --disassemble-all --section=.text --section=.data --section=.bss
RISCV_SIM = spike --extension=hwacha

vpath %.S $(isa_src_dir)



#------------------------------------------------------------
# Build assembly tests

%.hex: %
	elf2hex 16 16384 $< > $@

%.dump: %
	$(RISCV_OBJDUMP) $< > $@

%.out: %
	$(RISCV_SIM) $< 2> $@

$(tests): %: %.S
	$(RISCV_GCC) -m32 $(RISCV_GCC_OPTS) -I$(isa_src_dir)/../env/p/ -I$(isa_src_dir)/.. -T$(isa_src_dir)/../env/p/link.ld $< -o $@

tests_dump = $(addsuffix .dump, $(tests))
tests_hex = $(addsuffix .hex, $(tests))
tests_out = $(addsuffix .out, $(spike_tests))

run: $(tests_out)
	echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if /\*{3}(.{8})\*{3}(.*)/' \
	       $(tests_out); echo;

junk += $(tests) $(tests_dump) $(tests_hex) $(tests_out)

#--------------------------------------------------------------------
# Install tests
#--------------------------------------------------------------------
$(install_dir)/%: %
	cp $< $(install_dir)/;

$(install_dir):
	mkdir $(install_dir);

install: \
	$(install_dir) \
	$(addprefix $(install_dir)/, $(tests)) \
	$(addprefix $(install_dir)/, $(tests_dump)) \
	$(addprefix $(install_dir)/, $(tests_hex))


#------------------------------------------------------------
# Default

all: $(tests_dump) $(tests_hex)

#------------------------------------------------------------
# Clean up

clean:
	rm -rf $(junk)
