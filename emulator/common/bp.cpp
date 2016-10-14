#include "bp.h"
#include <map>

#define BUBBLE 0x4033


// Modify this line to substitute YOUR branch predictor for the
// default BTB...
#define BRANCH_PREDICTOR BTB


//
// Custom Branch Predictors
//

//  TODO: add your BranchPredictor implementations here!

class PredictorTemplate : public BranchPredictor
{
  private:
    // Define your predictor's internal data structures here
  public:
    PredictorTemplate ( struct bp_io& io ) : BranchPredictor ( io )
    {
      // Initialize your predictor state here
    }

    ~PredictorTemplate ( ) 
    {
      // delete any structures you heap-allocated here
    }

    uint32_t predict_fetch ( uint32_t pc )
    {
      // Predict the next PC here (or return 0 to predict not taken)
      return 0;
    }


    void update_execute ( uint32_t pc, 
                          uint32_t pc_next, 
                          bool mispredict,
                          bool is_brjmp, 
                          uint32_t inst )
    {
      if ( is_brjmp )
      {
        // update your predictor state here
      }

      // Note that this function is called for every inst, not just br/jmp!
    }
};


//
// Baseline Branch Predictor: simple BTB
//

#define BTB_ADDR_BITS 5
#define BTB_ENTRIES (1 << (BTB_ADDR_BITS-1))

// Sample branch predictor provided for you: a simple branch target buffer.
class BTB : public BranchPredictor 
{
  private:
    typedef struct {
      uint32_t target_pc;
      uint32_t tag_pc;
    }BTBEntry_t;
    BTBEntry_t* table;

  public:
    BTB ( struct bp_io& _io ) : BranchPredictor ( _io )
    {
      table = new BTBEntry_t[BTB_ENTRIES];
      memset ( table, 0, sizeof(BTBEntry_t) * BTB_ENTRIES );
    }

    ~BTB ( )
    {
      delete[] table;
    }
    
    // Given a PC, figure out which row of the BTB table to examine
    inline uint32_t index ( const uint32_t pc )
    {
      // Extract lower BTB_ADDR_BTS bits from (pc >> 2)
      // Shift PC right two because lower two bits always zero.
      const uint32_t mask = (1 << (BTB_ADDR_BITS-1)) - 1;
      return (pc >> 2) & mask;
    }

    uint32_t predict_fetch ( uint32_t pc )
    {
      BTBEntry_t entry = table[index ( pc )];

      // Only return a prediction of the entry's tag matches this
      // PC in order to avoid aliasing.
      if ( entry.tag_pc == pc ) 
        return entry.target_pc;

      return 0;
    }

    void update_execute ( uint32_t pc, 
                          uint32_t pc_next, 
                          bool mispredict,
                          bool is_brjmp, 
                          uint32_t inst )
    {
      if( inst == BUBBLE )
        return;

      uint32_t btb_index = index ( pc );
      BTBEntry_t new_entry, 
                       old_entry = table[btb_index];

      new_entry.target_pc = pc_next;
      new_entry.tag_pc = pc;

      if ( is_brjmp )
        table[btb_index] = new_entry;
    }
};


//
// A fully-associative, infinitely large BTB.  This code demonstrates how
// to use the STL map container as a fully-associative table.
//
class InfiniteBTB : public BranchPredictor
{
  private:
    // Map PC to Predicted Target or zero if none (PC+4)
    std::map<uint32_t, uint32_t> table;
  public:
    InfiniteBTB ( struct bp_io& _io ) : BranchPredictor ( _io ) { }
    ~InfiniteBTB ( ) { }

    uint32_t predict_fetch ( uint32_t pc )
    {
      if ( table.find( pc ) != table.end() ) // Does table contain pc?
        return table[pc];  
      return 0;
    }
    
    void update_execute ( 
        uint32_t pc, 
        uint32_t pc_next, 
        bool mispredict,
        bool is_brjmp, 
        uint32_t inst )
    {
      if ( is_brjmp )
        table[pc] = pc_next;
    }
};

//
// No Branch Predictrion: always predict "not taken"
//
class NoBP: public BranchPredictor
{
  public:
    NoBP ( struct bp_io& _io ) : BranchPredictor ( _io ) 
    {
    }

    ~NoBP ( ) 
    { 
    }

    uint32_t predict_fetch ( uint32_t pc )
    {
      return 0;
    }
    
    void update_execute ( 
        uint32_t pc, 
        uint32_t pc_next, 
        bool mispredict,
        bool is_brjmp, 
        uint32_t inst )
    {
    }
};




// 
// Set control signals to emulator
// (You should probably ignore this code)
//

void BranchPredictor::clock_lo ( dat_t<1> reset )
{
  if( reset.lo_word ( ) || !io.stats_reg_ptr->lo_word ( ) )
    return;

  // Examine instruction in execute stage and use it to call update_execute()
  update_execute_base (
      io.exe_reg_pc_ptr->lo_word ( ),
      io.exe_pc_next_ptr->lo_word ( ),
      io.exe_mispredict_ptr->lo_word ( ),
      // BR_N (no branch/jump) = 0 (see consts.scala)
      io.exe_br_type_ptr->lo_word ( ) != 0, 
      io.exe_reg_inst_ptr->lo_word ( ) );
}



void BranchPredictor::clock_hi ( dat_t<1> reset ) 
{
  if ( reset.lo_word ( ) ) 
    return;

  // Extract PC of instruction being fetched, and call predict_fetch with it,
  // and use the prediction to set relevant control signals back in the 
  // processor.
  uint32_t if_pc        = io.if_pc_reg_ptr->lo_word();
  uint32_t if_pred_targ = predict_fetch ( if_pc );
  *(io.if_pred_target_ptr) = LIT<32>( if_pred_targ );
  *(io.if_pred_taken_ptr) = LIT<1>( if_pred_targ != 0 );
}


void BranchPredictor::update_execute_base ( 
                          uint32_t pc,        // PC of this inst (in execute)
                          uint32_t pc_next,   // actual next PC of this inst
                          bool mispredict,    // Did we mispredict?
                          bool     is_brjmp,  // is actually a branch or jump
                          uint32_t inst )     // The inst itself, in case you
{                                             // want to extract arbitrary info
  if ( inst != BUBBLE ) 
  {
    brjmp_count += 1 & (long) is_brjmp;
    inst_count++;
    mispred_count += 1 & (long) ( mispredict );
  }
  cycle_count++;
  update_execute ( pc, pc_next, mispredict, is_brjmp, inst );
}


BranchPredictor::BranchPredictor ( struct bp_io& _io )  : io(_io) 
{
  brjmp_count   = 0;
  mispred_count = 0;
  inst_count    = 0;
  cycle_count   = 0;
}


BranchPredictor::~BranchPredictor ( )
{
  fprintf ( stderr, "## BRJMPs %ld\n", brjmp_count );
  fprintf ( stderr, "## INSTS %ld\n", inst_count );
  fprintf ( stderr, "## MISREDICTS %ld\n", mispred_count );
  fprintf ( stderr, "## CYCLES %ld\n", cycle_count );
}


BranchPredictor* BranchPredictor::make_branch_predictor ( struct bp_io& io )
{
  return new BRANCH_PREDICTOR ( io );
}

