module SyncMem
  #(parameter NUM_BYTES = (1 << 21),
    parameter DATA_WIDTH_HTIF = 32,
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
   reg [ADDR_WIDTH-1:0] regdataInstr_0_addr;
   reg [ADDR_WIDTH-1:0] regdataInstr_1_addr;
   reg [ADDR_WIDTH-1:0] reghr_addr;

    always_ff @ (posedge clk) begin
      reghr_addr <= hr_addr;
      regdataInstr_0_addr <= dataInstr_0_addr;
      regdataInstr_1_addr <= dataInstr_1_addr;
    end

    genvar i;
  generate
    for (i = 0; i < MASK_WIDTH_HTIF; i = i + 1) begin : gen_sel_writes
      always @ (posedge clk) begin
        if (hw_en) begin
          if (hw_mask[i] == 1'b1) begin
            mem[hw_addr + i] <= hw_data[i*8 +: 8];
          end
        end
      end
    end
  endgenerate

  generate
    for (i = 0; i < MASK_WIDTH_CPU; i = i + 1) begin : gen_sel_writes1
      always @ (posedge clk) begin
        if (dw_en) begin
          if (dw_mask[i] == 1'b1) begin
            mem[dw_addr + i] <= dw_data[i*8 +: 8];
          end
        end
      end
    end
  endgenerate 

  generate
    for (i = 0; i < MASK_WIDTH_HTIF; i = i + 1) begin : gen_sel_read
      always @ (posedge clk) begin
        hr_data[i*8 +: 8] = mem[reghr_addr + i];
      end
    end 
  endgenerate

  generate
    for (i = 0; i < MASK_WIDTH_CPU; i = i + 1) begin : gen_sel_read1
      always @ (posedge clk) begin
        dataInstr_0_data[i*8 +: 8] = mem[regdataInstr_0_addr + i];
        dataInstr_1_data[i*8 +: 8] = mem[regdataInstr_1_addr + i];
      end
    end 
  endgenerate
   
endmodule // AsyncReadMem

