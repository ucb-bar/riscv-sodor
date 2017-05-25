module AsyncReadMem
  #(parameter NUM_BYTES = (1 << 21),
    parameter DATA_WIDTH_HTIF = 64,
    parameter DATA_WIDTH_CPU = 32)
   (input clk,
    
    input [ADDR_WIDTH-1:0] hw_addr,
    input [DATA_WIDTH_HTIF-1:0] hw_data,
    input [MASK_WIDTH_HTIF-1:0] hw_mask,
    input hw_en,

    input [ADDR_WIDTH-1:0] hr_addr,
    output [DATA_WIDTH_HTIF-1:0] hr_data,

    input [ADDR_WIDTH-1:0] dw_addr,
    input [DATA_WIDTH_CPU-1:0] dw_data,
    input [MASK_WIDTH_CPU-1:0] dw_mask,
    input dw_en,

    input [ADDR_WIDTH-1:0] dataInstr_0_addr,
    output [DATA_WIDTH_CPU-1:0] dataInstr_0_data,
    input [ADDR_WIDTH-1:0] dataInstr_1_addr,
    output [DATA_WIDTH_CPU-1:0] dataInstr_1_data
    );

   localparam ADDR_WIDTH = $clog2(NUM_BYTES);
   localparam MASK_WIDTH_HTIF = DATA_WIDTH_HTIF >> 3;
   localparam MASK_WIDTH_CPU = DATA_WIDTH_CPU >> 3;   

   reg [7:0] 		mem [0:NUM_BYTES-1];

   // TODO: the actual accesses

`ifdef verilator
   export "DPI-C" task do_readmemh;

   task do_readmemh;
      input string file;
      $readmemh(file, mem);
   endtask

    // Function to access RAM (for use by Verilator).
   function [7:0] get_mem;
      // verilator public
      input [ADDR_WIDTH-1:0] addr; // word address
      get_mem = mem[addr];
   endfunction

   // Function to write RAM (for use by Verilator).
   function set_mem;
      // verilator public
      input [ADDR_WIDTH-1:0] addr; // word address
      input [7:0] 	     data; // data to write
      mem[addr] = data;
   endfunction // set_mem
`endif
   
endmodule // AsyncReadMem

