#=======================================================================
# UCB VLSI FLOW: Makefile for riscv-bmarks
#-----------------------------------------------------------------------
# Yunsup Lee (yunsup@cs.berkeley.edu)
#

default: all

bmarkdir = .

pkdir = ../../riscv-pk/build/

instname    = riscv-bmarks
instbasedir = ../../install


# allow this to be overridden
SUPERVISOR_MODE?="false"
RV64_MODE?="false"

#--------------------------------------------------------------------
# Sources
#--------------------------------------------------------------------

bmarks = \
	median \
	mix_manufacturing \
	multiply \
	qsort \
	towers \
	vvadd \

#	dhrystone \
	stanford \

#--------------------------------------------------------------------
# Build rules
#--------------------------------------------------------------------

HOST_OPTS = -DPREALLOCATE=0 -DHOST_DEBUG=1
HOST_COMP = gcc $(HOST_OPTS)

RISCV_GCC = riscv-gcc -I$(bmarkdir)/common
RISCV_GCC_OPTS = -DSET_STATS -O2 -g -nostdlib -nostartfiles 
#RISCV_OBJDUMP = riscv-objdump --disassemble-all --disassemble-zeroes --section=.text --section=.data
RISCV_OBJDUMP = riscv-objdump --disassemble-all --disassemble-zeroes 

RISCV_USER_SIM=spike
RISCV_SPVR_SIM=PATH=$(pkdir):${PATH}; spike

ifeq ($(SUPERVISOR_MODE),"false")
	RISCV_GCC_OPTS +=
	RISCV_LINK = riscv-gcc -T $(bmarkdir)/common/test.ld
	RISCV_SIM = $(RISCV_USER_SIM)
else
	RISCV_GCC_OPTS += -DUSE_SUPERVISOR_MODE 
	RISCV_LINK = riscv-gcc 
	RISCV_SIM = $(RISCV_SPVR_SIM) pk
endif

ifeq ($(RV64_MODE),"false")
	RISCV_GCC += -m32
	RISCV_LINK += -m32
endif

VPATH += $(addprefix $(bmarkdir)/, $(bmarks))

incs  += -I. $(addprefix -I$(bmarkdir)/, $(bmarks))
objs  :=
deps  += 

include $(patsubst %, $(bmarkdir)/%/bmark.mk, $(bmarks))

#------------------------------------------------------------
# Build and run benchmarks on riscv simulator

bmarks_riscv_bin  = $(addsuffix .riscv,  $(bmarks))
bmarks_riscv_dump = $(addsuffix .riscv.dump, $(bmarks))
bmarks_riscv_out  = $(addsuffix .riscv.out,  $(bmarks))
bmarks_riscv_hex  = $(addsuffix .riscv.hex,  $(bmarks)) # only available in -testrun mode

bmarks_defs   = -DPREALLOCATE=0 -DHOST_DEBUG=0
bmarks_cycles = 1000000

%.hex: %
	elf2hex 16 16384 $< > $@

$(bmarks_riscv_dump): %.riscv.dump: %.riscv
	$(RISCV_OBJDUMP) $< > $@

$(bmarks_riscv_out): %.riscv.out: %.riscv
	$(RISCV_SIM) $< &> $@

%.o: %.c $(deps)
	$(RISCV_GCC) $(RISCV_GCC_OPTS) $(bmarks_defs) \
	             -c $(incs) $< -o $@

%.o: %.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) $(bmarks_defs) \
	             -c -I$(riscv_gcc_incdir) $(incs) $< -o $@



#riscv: $(bmarks_riscv_dump) 
riscv: $(bmarks_riscv_dump) $(bmarks_riscv_hex)
run-riscv: $(bmarks_riscv_out)
	echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if /\*{3}(.{8})\*{3}(.*)/' \
	       $(bmarks_riscv_out); echo;

run: run-riscv

junk += $(bmarks_riscv_bin) $(bmarks_riscv_spvr) $(bmarks_riscv_dump) $(bmarks_riscv_spvr_dump) $(bmarks_riscv_out) $(bmarks_riscv_hex) 

#------------------------------------------------------------
# Build and run benchmarks on host machine

bmarks_host_bin = $(addsuffix .host, $(bmarks))
bmarks_host_out = $(addsuffix .host.out, $(bmarks))

$(bmarks_host_out): %.host.out: %.host
	./$< > $@

host: $(bmarks_host_bin)
run-host: $(bmarks_host_out)
	echo; perl -ne 'print "  [$$1] $$ARGV \t$$2\n" if /\*{3}(.{8})\*{3}(.*)/' \
	       $(bmarks_host_out); echo;

junk += $(bmarks_host_bin) $(bmarks_host_out)

#------------------------------------------------------------
# Default

all: riscv

#------------------------------------------------------------
# Install

date_suffix = $(shell date +%Y-%m-%d_%H-%M)
#install_dir = $(instbasedir)/$(instname)-$(date_suffix)

install_dir = $(instbasedir)/$(instname)

latest_install = $(shell ls -1 -d $(instbasedir)/$(instname)* | tail -n 1)

# replace ".riscv" with "rv32x" when installing the benchmark
ifeq ($(SUPERVISOR_MODE),"false")
	new_ext=.rv32u
else
	new_ext=.rv32s
endif

install:
	mkdir -p $(install_dir)
	for i in *.riscv; do cp $$i $${i/.riscv}$(new_ext); done
	cp -r $(bmarks_riscv_bin:.riscv=$(new_ext)) $(install_dir)
	rm -f *$(new_ext) *$(new_ext).dump

#	for i in *.riscv.dump; do cp $$i $${i/.riscv.dump}$(new_ext).dump; done
#	cp -r $(bmarks_riscv_dump:.riscv.dump=$(new_ext).dump) $(install_dir)
#	cp -r $(bmarks_riscv_bin) $(bmarks_riscv_dump) $(install_dir)

install-link:
	rm -rf $(instbasedir)/$(instname)
	ln -s $(latest_install) $(instbasedir)/$(instname)

#------------------------------------------------------------
# Clean up

clean:
	rm -rf $(objs) $(junk) *.rv32s *.rv64s *.rv32u *.hex *.dump *.riscv *.out *.o 
