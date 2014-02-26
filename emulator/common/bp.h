#include "common.h"
#include "emulator.h"

// Signals in the emulator that the simulated branch predictor reads and
// writes.  Create an instance of this structure and set all the pointers
// to the relevant signals defined in generated-src/Top.h before passing it
// to the BranchPredictor constructor.
struct bp_io
{
  // Inputs
  dat_t<32>* if_pc_reg_ptr;
  dat_t<32>* exe_reg_pc_ptr;
  dat_t<32>* exe_pc_next_ptr;
  dat_t<4>*  exe_br_type_ptr;
  dat_t<32>* exe_reg_inst_ptr;
  dat_t<1>*  exe_mispredict_ptr;
  dat_t<1>* stats_reg_ptr;

  // Outputs
  dat_t<1>*  if_pred_taken_ptr;
  dat_t<32>* if_pred_target_ptr;
};

class BranchPredictor 
{
  private:
    long brjmp_count, inst_count, mispred_count, cycle_count;

  public:
    BranchPredictor ( struct bp_io& _io );
    // Destructor must be virtual so that the delete at the end of main()
    // in emulator.cpp will call the child's destructor.
    ~BranchPredictor ( );

    // This static function is called by main() in emulator.cpp, which uses
    // whatever particular class of branch predictor you choose to return.
    // (Thus, to choose which implementation gets run, modify this function!)
    static BranchPredictor* make_branch_predictor ( struct bp_io& io );

    // 
    // Functions to be implemented by each branch predictor:
    // You need to fill these in.
    //
    
    // In fetch stage, where only PC is known so far.  Returns
    // target when predicting taken, or 0 otherwise.
    virtual uint32_t predict_fetch ( uint32_t pc ) {}

    // In decode, a cycle later, a change to revise prediction based on
    // what the instruction is (one cycle penalty to kill IF stage,
    // as opposed to two if decided in next stage).  Return value follows
    // same scheme as predict_fetch.
    // virtual uint32_t predict_decode ( uint32_t pc, uint32_t inst);
 
    // Use this to update your predictor once the actual target of an 
    // instruction is known.
    virtual void update_execute ( 
                          uint32_t pc,        // PC of this inst (in execute)
                          uint32_t pc_next,   // actual next PC of this inst
                          bool mispredict, // predict_fetch for this inst
                          bool     is_brjmp,  // is actually a branch or jump
                          uint32_t inst )     // The inst itself, in case you
    { }                                       // want to extract arbitrary info 

    // 
    // Functions written for you: these just get called by the emulator 
    // and will invoke your predict() and update() functions appropriately.
    //

    // Should be called in the main loop in emulator.cpp just before calling
    // the analogous functions in the DUT.
    void clock_lo ( dat_t<1> reset );
    void clock_hi ( dat_t<1> reset );

  private:
    struct bp_io io;

    //
    // clock_lo and clock_hi call these functions before they then
    // call the corresponding virtual functions in the child.
    //

    void update_execute_base ( 
        uint32_t pc, 
        uint32_t pc_next, 
        bool mispredict,
        bool is_brjmp, 
        uint32_t inst );
};



