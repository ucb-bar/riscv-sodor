#=======================================================================
# UCB CS250 Makefile fragment for benchmarks
#-----------------------------------------------------------------------
#
# Each benchmark directory should have its own fragment which
# essentially lists what the source files are and how to link them
# into an riscv and/or host executable. All variables should include
# the benchmark name as a prefix so that they are unique.
#

mix_manufacturing_c_src = \
	mix_manufacturing_main.c \

mix_manufacturing_riscv_src = \

mix_manufacturing_c_objs     = $(patsubst %.c, %.o, $(mix_manufacturing_c_src))
mix_manufacturing_riscv_objs = $(patsubst %.S, %.o, $(mix_manufacturing_riscv_src))

mix_manufacturing_host_bin = mix_manufacturing.host
$(mix_manufacturing_host_bin) : $(mix_manufacturing_c_src)
	$(HOST_COMP) $^ -o $(mix_manufacturing_host_bin)

mix_manufacturing_riscv_bin = mix_manufacturing.riscv
$(mix_manufacturing_riscv_bin) : $(mix_manufacturing_c_objs) $(mix_manufacturing_riscv_objs)
	$(RISCV_LINK) $(mix_manufacturing_c_objs) $(mix_manufacturing_riscv_objs) -o $(mix_manufacturing_riscv_bin)

junk += $(mix_manufacturing_c_objs) $(mix_manufacturing_riscv_objs) \
        $(mix_manufacturing_host_bin) $(mix_manufacturing_riscv_bin)
