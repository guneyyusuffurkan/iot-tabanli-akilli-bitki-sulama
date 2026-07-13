class Bankaccount:
    def __init__(self,bakiye):
        self.__balance = bakiye

    def deposit(self,miktar):
        self.__balance += miktar

    def withdraw(self,miktar) :
        if self.__balance >= miktar:
            self.__balance -=  miktar
        else:
            print("Yetersiz Bakiye")    

    def get_balance(self):
        return self.__balance
