module AsyncReadMem
  #(parameter NUM_BYTES = (1 << 21),
    parameter DATA_WIDTH = 32)
   (input clk,
    
    input [ADDR_WIDTH-1:0] hw_addr,
    input [DATA_WIDTH-1:0] hw_data,
    input [MASK_WIDTH-1:0] hw_mask,
    input hw_en,

    input [ADDR_WIDTH-1:0] hr_addr,
    output [DATA_WIDTH-1:0] hr_data,

    input [ADDR_WIDTH-1:0] dw_addr,
    input [DATA_WIDTH-1:0] dw_data,
    input [MASK_WIDTH-1:0] dw_mask,
    input dw_en,

    input [ADDR_WIDTH-1:0] dataInstr_0_addr,
    output [DATA_WIDTH-1:0] dataInstr_0_data,
    input [ADDR_WIDTH-1:0] dataInstr_1_addr,
    output [DATA_WIDTH-1:0] dataInstr_1_data
    );

   localparam ADDR_WIDTH = $clog2(NUM_BYTES);
   localparam MASK_WIDTH = DATA_WIDTH >> 3;

   reg [7:0] 		mem [0:NUM_BYTES-1];
   
  genvar i;
  generate
    for (i = 0; i < MASK_WIDTH; i = i + 1) begin : gen_sel_read
      always @ (posedge clk) begin
        if (hw_en) begin
          if (hw_mask[i] == 1'b1) begin
            mem[hw_addr + i] <= hw_data[i*8 +: 8];
          end
        end
        if (dw_en) begin
          if (dw_mask[i] == 1'b1) begin
            mem[dw_addr + i] <= dw_data[i*8 +: 8];
          end
        end
      end
      always @* begin
        hr_data[i*8 +: 8] = mem[hr_addr + i];
        dataInstr_0_data[i*8 +: 8] = mem[dataInstr_0_addr + i];
        dataInstr_1_data[i*8 +: 8] = mem[dataInstr_1_addr + i];
      end
    end 
  endgenerate
   
endmodule // AsyncReadMem