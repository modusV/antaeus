# Antaeus

In the following paragraphs, I will illustrate how I tackled the problem since I started reading it.

## Architecture

To begin with, I went through the README file, and I looked at the folder structure. Then, I run the application in my IDE using docker, and I tried out the REST endpoints.
From what I understood reading the requirements, I thought that a possible implementation could include these components:

1. A scheduler that manages a recurring task in charge of the payment operation for all pending invoices, to be executed on the first of the month, plus other extra jobs for failure handling.
2. A service to pay the invoices, which I called `InvoicePaymentService`. It is useful to interface the scheduled jobs with the data layer and the `BillingService` (which contains the calls to the external APIs). In this service, we need a function to pay each invoice with a particular status, to directly call the service depending on which ones we need to assess.

## Implementation

### Scheduler

Before choosing my solution, I evaluated different choices:

- First of all, I tried to understand whether there was a way to detach the payment of the invoices from the application, to create a logically dissociated service built specifically for this task (to follow the separation of concerns paradigm, making it more readable and fault-tolerant).

  Given that from our first call, I understood that Pleo infrastructure makes use of a Kubernetes cluster, we could leverage Kubernetes CronJobs, which come in hand for situations similar to this one, when we are presented a recurring task. The problem with them is that it is pretty hard to understand failure reasons unless a detailed support infrastructure is built in advance, and for the sake of this exercise, it would add too much complexity.

  Moreover, If we want an even more reliable solution, I believe that AWS, GCP ... offer a paid solution for a job scheduler.

- Another solution along this line would be leveraging Redis's Redisson library. If Pleo already has a Redis server, a Redisson client would represent a viable way to schedule tasks. For the same reason as above, I looked for a more straightforward option.

- Successively,  I searched for something similar to Java Spring Boot's `@Scheduled`, so that I could set a precise date and time on which a specific task was triggered (using cron expressions, for example). I could not find anything native apart from the simple Timer library, and I did not want to include another external dependency to keep the application more compact, so I discarded this option.

These problematics brought me to manually implementing an `InvoicePaymentScheduler`, which leverages Java's `ScheduledExecutors` class to service all the jobs regarding invoices, which are provided via `Runnable` objects calling the `InvoicePaymentService`.

I wrote the scheduler so that it runs the tasks asynchronously but on a single thread to avoid overlaps and concurrency issues on its side. I decided to proceed with this approach because the payment operation is performed once per month anyway.

Thinking again in a production mindset, to better assess the case in which the app crashes the first day of the month during the billing phase, we could add a date to each invoice. In this way, we could bill the users on the day they started their subscription (closer to a real-world case), and we would know which ones should have been paid by the $n_{th}$ day, and which ones belong to the new month.

### PaymentService

Here, the first thing I thought was that it could run its payment operation synchronously (ease of use) or asynchronously (much faster due to API calls response times and database reads/writes) but needing to be adequately managed.

Given that we are using `Sqlite3` as a database, a fully concurrent solution is not feasible because simultaneous operations called from different threads could cause access problems (if we were using a SQL server, we could have leveraged row-level locking to perform how many concurrent updates as we want).

That is why I opted for a half-way solution:

- I used Kotlin's coroutines to run "in parallel" all the operations on the same thread. In this way, even if the API requests would have a delay, that operation could be suspended and resumed when the response is available while still maintaining DB access safe.

We should notice that the job's nature is very delicate, so we need to handle failures in the correct way. In fact, we are dealing with a very sensitive task (money are involved!), and for this reason, we want to avoid at any cost downtime and/or mistakes because users REALLY dislike being charged twice, and we REALLY dislike not charging them when it is due.

Because of this, for each invoice the process is the following:

1. Write to the invoice's record that the payment operation is starting (change status on DB to `PAYING`).
2. Send the request to the bank's API.
3. Write on DB the request's result (see below for flags).

Using these states, we can assess crashes at each point:

- If the app crashes before 1, the invoice will result as unpaid on the DB and will be paid by the next task.
- If the app crashes during or after 2, we can run another job to ensure the nature of each "unfulfilled" request (e.g. check with the bank if the payment happened or not). In fact, in our DB we will have stored all the pending transactions, and we can investigate further whether those were completed or not, without the risk of billing some users twice or losing money.
- If it crashes after 3, we have already updated the status, and there is no problem.

Successively, when all the payments resolve, we perform the following actions, depending on the status of the response:

- `PAID`, we don't need to perform any further step. Do not notify the user; the bank will do it, and  – I think – it is better to avoid stressing that some money has been taken from him.
- `FAILED_EMPTY_ACCOUNT`, meaning failure due to money shortage. In this case, we should block the account and notify the user to update his payment method. We could do this in a smart way, as Netflix does, do not block him out of the application, but whenever he tries to perform an operation, stop him with a pop-up asking to update the payment method. It creates the need to pay as soon as possible because he is needing the service's functionality in that precise moment!
- `FAILED_CUSTOMER_NOT_FOUND`, meaning failure due to user not found. The user is not found by the payment provider; I can imagine that this event occurs only if the payment provider had a database corruption, or if the user changed payment provider. In this case, it is not a fault on our part, so I would proceed in the same way as the `FAILED_EMPTY_ACCOUNT` case to tell the user to update the payment method.
- `FAILED_CURRENCY_MISMATCH`, meaning failure due to wrong currency.  Given that we are dealing with subscriptions, I can guess that each invoice represents the amount due for $n$ Pleo users in a company for a month. If the bank's API tells us that the currency is wrong for that specific customer, it could mean that the company has different payment methods or that we recorded on our invoice's record another currency with respect to the customer's account default one. In this case, we could perform again the payment calling `manageCurrencyMismatch`, specifying the preferred currency; probably, the bank API has a function for it (e.g. if we have the invoice in pounds and the payment provider throws wrong currency, we check the user's preferred currency and we find \$. We then can send another request to the bank asking to withdraw that amount of £ converted in \$).
- `FAILED_NETWORK`, meaning failure due to network errors. If, among all requests, some failed due to network errors, we could schedule another job that, after $t$ time, will retry to withdraw the sum again. This operation can be repeated $n$ times depending on implementation preferences and usual network recovery time. If there are still some pending invoices after all the tries, I would put these account on hold and investigate further on their cause with another task.



### Final Thoughts

Before wrapping up, we must consider the eventuality of a server/app crash in the different phases:

- If it happens during the payment phase, we said before that the function is robust to this event.
- If during server startup, we have `FAILED_NETWORK` or `PAYING` requests, it means that we are recovering from a crash, and we need to retry the payments in the first case or launch recovery in the second one.
- The only problem left would occur in the case of crash on the first of the month at midnight during the payment execution. This would cause a re-schedule for the following month; to solve this problem, the only solution would be to use dates associated to each `Invoice`(we cannot launch a payment job right away, because if new bills arrived we would make the user pay also for the incoming month in advance!).