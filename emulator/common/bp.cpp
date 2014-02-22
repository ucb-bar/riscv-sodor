#include "bp.h"


//
// Custom Branch Predictors
//

//  TODO: add your BranchPredictor implementations here!

//
// Baseline Branch Predictor: simple BTB
//

BTB::BTB ( struct bp_io& _io ) : BranchPredictor ( _io )
{
  table = new struct btb_entry[BTB_ENTRIES];
  memset ( table, 0, sizeof(struct btb_entry) * BTB_ENTRIES );
}

BTB::~BTB ( ) 
{
  delete[] table;
}

// Given a PC, figure out which row of the BTB table to examine
inline uint32_t index ( uint32_t pc )
{
  // Extract lower BTB_ADDR_BTS bits from (pc >> 2)
  // Shift PC right two because lower two bits always zero.
  const uint32_t mask = (1 << (BTB_ADDR_BITS-1)) - 1;
  return (pc >> 2) & mask;
} 
  


uint32_t BTB::predict_fetch ( uint32_t pc )
{
  struct btb_entry entry = table[index ( pc )];
  
  // Only return a prediction of the entry's tag matches this
  // PC in order to avoid aliasing.
  if ( entry.tag_pc == pc ) 
    return entry.target_pc;

  return 0;
}


void BTB::update_execute ( 
                       uint32_t pc, 
                       uint32_t pc_next, 
                       bool is_brjmp, 
                       uint32_t inst )
{
  if( inst == BUBBLE )
    return;
  
  uint32_t btb_index = index ( pc );
  struct btb_entry new_entry, 
                   old_entry = table[btb_index];

  new_entry.target_pc = pc_next;
  new_entry.tag_pc = pc;

  if ( is_brjmp )
    table[btb_index] = new_entry;
}



// 
// Set control signals to emulator
// (You should probably ignore this code)
//

void BranchPredictor::clock_lo ( dat_t<1> reset )
{
  if( reset.lo_word() )
    return;

    // Examine instruction in execute stage and use it to call update_execute()
  uint32_t exe_pc         = io.exe_reg_pc_ptr->lo_word();
  uint32_t exe_pc_next    = io.exe_pc_next_ptr->lo_word();
  uint32_t exe_br_type    = io.exe_br_type_ptr->lo_word();
  uint32_t exe_inst       = io.exe_reg_inst_ptr->lo_word();
  update_execute (
      exe_pc,
      exe_pc_next,
      exe_br_type != 0, // BR_N (no branch/jump) = 0 (see consts.scala)
      exe_inst);
}



void BranchPredictor::clock_hi ( dat_t<1> reset ) 
{
  if ( reset.lo_word() )
    return;

  // Extract PC of instruction being fetched, and call predict_fetch with it,
  // and use the prediction to set relevant control signals back in the 
  // processor.
  uint32_t if_pc        = io.if_pc_reg_ptr->lo_word();
  uint32_t if_pred_targ = predict_fetch ( if_pc );
  *(io.if_pred_target_ptr) = LIT<32>( if_pred_targ );
  *(io.if_pred_taken_ptr) = LIT<1>( if_pred_targ != 0 );
}



