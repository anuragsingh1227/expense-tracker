# Internet SMS corpus audit

- Total: 110
- Pass: 107
- Fail: 3
- Failure rate: 2.73%
- Ignore-correct: 45
- Keep-correct: 62

## Failures

### FAIL keep-sbi-upi-zomato-015
Sender: VM-SBIINB
SMS: A/c XX9876 debited by INR 350.00 on 08-Aug-26 to ZOMATO via UPI Ref 812345678901. Avl Bal INR 19,650.00
Expected: keep=true type=DEBIT category=Food amount=350.00 merchant~=Zomato spend=true
Actual: keep=false type=null category=null amount=null merchant=null spend=null
Gap: Parser/gate rejected an expected ledger movement.

### FAIL keep-sbi-imps-owner-transfer-016
Sender: VM-SBIINB
SMS: A/c XX9876 debited by Rs. 15,000.00 on 05-Aug-26 via IMPS to ANUR Ref IMPS621706928597. Avl Bal Rs 44,996.19
Expected: keep=true type=DEBIT category=Transfer amount=15000.00 merchant~=null spend=false
Actual: keep=false type=null category=null amount=null merchant=null spend=null
Gap: Parser/gate rejected an expected ledger movement.

### FAIL keep-sbi-neft-rent-018
Sender: VM-SBIINB
SMS: A/c XX9876 debited by INR 22000.00 on 03-Aug-26 for NEFT RENT to landlord Ref N246813579. Avl Bal INR 60,000.00
Expected: keep=true type=DEBIT category=Rent amount=22000.00 merchant~=null spend=true
Actual: keep=false type=null category=null amount=null merchant=null spend=null
Gap: Parser/gate rejected an expected ledger movement.

