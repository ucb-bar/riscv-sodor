#=======================================================================
# UCB CS250 Makefile fragment for benchmarks
#-----------------------------------------------------------------------
#
# Each benchmark directory should have its own fragment which
# essentially lists what the source files are and how to link them
# into an riscv and/or host executable. All variables should include
# the benchmark name as a prefix so that they are unique.
#

stanford_c_src = \
	stanford.c \

stanford_riscv_src = \

stanford_c_objs     = $(patsubst %.c, %.o, $(stanford_c_src))
stanford_riscv_objs = $(patsubst %.S, %.o, $(stanford_riscv_src))

stanford_host_bin = stanford.host
$(stanford_host_bin): $(stanford_c_src)
	$(HOST_COMP) $^ -o $(stanford_host_bin)

stanford_riscv_bin = stanford.riscv
$(stanford_riscv_bin): $(stanford_c_objs) $(stanford_riscv_objs)
	$(RISCV_LINK) $(stanford_c_objs) $(stanford_riscv_objs) \
    -o $(stanford_riscv_bin) $(RISCV_LINK_OPTS)

junk += $(stanford_c_objs) $(stanford_riscv_objs) \
        $(stanford_host_bin) $(stanford_riscv_bin)
