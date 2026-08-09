# Internet SMS corpus failures

- Total: 120
- Fail: 7
- Failure rate: 5.83%

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

### FAIL keep-sbi-transferred-upi-111
Sender: VM-SBIINB
SMS: Rs.450.00 transferred from A/c XX1234 on 15-APR via UPI to BIGBASKET Ref 412839. Avl Bal Rs 8,200.00
Expected: keep=true type=DEBIT category=Groceries amount=450.00 merchant~=BigBasket spend=true
Actual: keep=false type=null category=null amount=null merchant=null spend=null
Gap: Parser/gate rejected an expected ledger movement.

### FAIL keep-cheque-cleared-credit-112
Sender: VM-HDFCBK
SMS: Cheque No. 123456 for Rs.50,000.00 has been cleared in your A/c XX1234 on 06-08-26. Avl Bal Rs 1,20,000.00
Expected: keep=true type=CREDIT category=Transfer amount=50000.00 merchant~=null spend=false
Actual: keep=false type=null category=null amount=null merchant=null spend=null
Gap: Parser/gate rejected an expected ledger movement.

### FAIL keep-cash-deposit-113
Sender: VM-AXISBK
SMS: Cash deposit of INR 10,000.00 in A/c XX8291 on 02-08-26. Avl Bal INR 30,000.00
Expected: keep=true type=CREDIT category=Transfer amount=10000.00 merchant~=null spend=false
Actual: keep=false type=null category=null amount=null merchant=null spend=null
Gap: Parser/gate rejected an expected ledger movement.

### FAIL ignore-emi-converted-ack-119
Sender: AX-ICICIB
SMS: Your transaction of Rs 8,646.47 at Avenue Supermar on Card XX1014 has been converted to EMI. EMI of Rs 864 starts from 05-Aug-26.
Expected: keep=false type=null category=null amount=null merchant~=null spend=null
Actual: keep=true type=DEBIT category=Groceries amount=8646.47 merchant=DMart spend=true
Gap: Gate kept a non-ledger acknowledgement, status, OTP, or promo SMS.

