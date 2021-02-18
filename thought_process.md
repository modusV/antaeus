# How

In the following paragraphs I will illustrate how I tackled the problem since the first time I started reading it.

## First steps

To begin with, I read the readme and requirements, and I looked at the folder structure. Then, I run the application in my IDE using docker and tested the REST endpoints.

From what I understood from the requirements and my implementation choices, new functionalities shoud look like this:

1. Schedule a recurring task that triggers the payment operation for all pending invoices on the first of the month. If we would want to bill the users on the day in which they started their subscription, I would add a due date field to the invoices and run this recurring task everyday to service all the expired invoices.
2. Create a billing job for each invoice that needs to be payed, add the job to a queue.
3. Run a pool of threads on the queue to fulfill all jobs, it can be done synchronously (ease of use), or asyncronously with callbacks (much faster due to API call response times and database reads/writes) but needs to be properly managed.
4. When the payments resolves, we need to perform the following actions, depending on the status of the respose:
   - Completed, we don't need to perform any further step. Do not notify the user, the bank will do it, and also I think it is better to avoid stressing that some money has been taken from him.
   - Failed due to money shortage. In this case, block the account and notify the user to update his payment method. We could do this in a smart way, like Netflix does, do not block him out of the application, but whenever he tries to perform an operation block him with a pop-up that asks him to update the payment method. It creates the need to pay as soon as possible, because he is needing the sevice's functionality in that precise moment!
   - Failed due to User not found. The user is not found by the payment provider; I can imagine that this event occurrs only if the payment provider had a database corruption, or if the user changed payment provider. In this case, is not a fault on our part, so I would proceed in the same way of the 'Failed due to money shortage' case.
   - Failed due to wrong currency. Enqueue another job request to the bank that uses another API where we can specify the currency conversion that has to be made. (e.g. if we have the invoice in pounds and the payment provider throws wrong currency, we check the user's preferred currency and we find \$. We then can send another request to the bank asking to witdraw that amount of £ converted in \$).
   - Failed due to network errors. If, among all requests, some failed due to network errors, I would schedule another job that, after $t$ time, will retry to withdraw again the sum. This can be repeated $n$ times depending on implementation preferences and usual network recovery time. If there are still some pending invoices after all the tries, I would put these account on hold and investigate further with another task on the reason.
5. Before wrapping up, we must consider the eventuality of a server/app crash halfway a payment. For this reason I would proceed in this way:
   - Once a payment is retrieved from the queue, in a single transaction change its state on the database to 'Pending' and send the request to the payment API, if a crash occurrs, rollback the DB operation. 
   - When the response is received, change the DB status to 'PAID' or 'FAILED' in a transaction. If a crash occurrs before or while payment outcome arrives, in our DB we will have stored all the transactions that were pending, and we can investigate further with another task whether those were completed or not, without the risk to bill the user twice or lose money.

To perform the steps above, some additional fields are needed in the database:

- In the user object, a field identifying whether the user's account is active or not.
- In the invoice object, additional flags for the invoice status, to better understand the status of the request (PAID, PENDING, TO_PAY, FAILED_NETWORK_ERROR, FAILED_EMPTY_ACCOUNT).

To better understand how recurring tasks are implemented in a production environment I proceeded in this way:

- The first thing I looked for, was something like Java Spring's `@Scheduled`,
so that I could set a precise date on which a specific task was triggered. 

The problem of this approach resides in the nature of the scheduled job. We are dealing with a very sensitive task (money are involved!), and for this reason we want to avoid at any cost downtime and/or mistakes, because users REALLY dislike being charged twice, and we REALLY dislike not charging them when it is due.

So, before walking this path, I tried to understand whether there was a way to detach the payment of the invoices from the application, to create a logically dissociated service that has only that objective (to follow the separation of concerns paradigm, making it more readable and fault-tolerant).

Given that from our first call, I understood that Pleo infrastructure makes use of a Kubernetes cluster, we could leverage Kubernetes CronJobs, which come in hand for situations similar to this one, when we are presented a recurring task. The problem with them is that is pretty hard to understand failure reasons unless a detailed support infrastructure is built in advance, and for the sake of this exercise it would add too much complexity.

