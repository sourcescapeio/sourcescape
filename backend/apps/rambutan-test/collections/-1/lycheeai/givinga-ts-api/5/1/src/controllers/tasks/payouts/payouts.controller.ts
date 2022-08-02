import { Body, Controller, Get, Post, Put, Req, UseFilters, Param, HttpCode, ParseIntPipe, Query, UsePipes, ValidationPipe } from '@nestjs/common';
import { ErrorFilter } from '../../../libs/filters/error.filter';
import { Request, response } from 'express';
import { HoneycombKeys, HoneycombService } from '../../../libs/honeycomb/honeycomb.service';
import { AuthService } from '../../../services/auth/auth.service';
import { AdminPermission } from '../../../enums/admin-permission';
import { RealIP } from 'nestjs-real-ip';
import { APIError } from '../../../libs/errors/api-error';
import { IsEmail, IsString, IsOptional, IsArray, IsInt } from 'class-validator';
import { camelize } from '@angular-devkit/core/src/utils/strings';
import { Charity, CharityBillComVendor, CharityHyperwalletPaperCheck, CharityHyperwalletUser, HydratedCharity, Prisma, PrismaClient, Transaction, TransactionDisbursement } from '@prisma/client';
import { PrismaService } from '../../../libs/prisma/prisma.service';
import { TransactionsDao } from '../../../dao/transactions/transactions.dao';
import { convert, LocalDate, LocalDateTime } from 'js-joda';
import * as _ from 'lodash';
import { FundedAccountType } from '../../../enums/funded-account-type';
import { FeatureFlagDao } from '../../../dao/feature-flag/feature-flag.dao';
import { FeatureFlag } from '../../../enums/feature-flag';
import { CreditCardDao } from '../../../dao/credit-card/credit-card.dao';
import { EmployeesDao } from '../../../dao/employees/employees.dao';
import { PaymentStatusType, PaymentStatusTypeByBillCom, PaymentStatusTypeByName } from '../../../enums/payment-status-type';
import { CharitiesDao } from '../../../dao/charities/charities.dao';
import { CharitySource } from '../../../enums/charity-source';
import { CharityHyperwalletUserDao } from '../../../dao/charity-hyperwallet-user/charity-hyperwallet-user.dao';
import { assert } from 'console';
import { CorporationWebhookService } from '../../../services/corporation-webhook/corporation-webhook.service';
import { Bill, EarthshareBillComService, GivingaBillComService } from '../../../services/bill-com/bill-com.service';
import { shouldUpdate, TransactionDisbursementDao } from '../../../dao/transaction-disbursement/transaction-disbursement.dao';
import { PaymentMethodType, PaymentMethodTypeByBillCom, PaymentMethodTypeByName } from '../../../enums/payment-method-type';
import { DisbursementType } from '../../../enums/disbursement-type';
import { HyperwalletService } from '../../../services/hyperwallet/hyperwallet.service';
import { CharityBillComVendorDao } from '../../../dao/charity-bill-com-vendor/charity-bill-com-vendor.dao';
import { EmailService } from '../../../services/email/email.service';
import { CONST_NOTIFICATIONS_EMAIL } from '../tasks/tasks.controller';
import * as Sentry from '@sentry/node';
import { ApprovalStatusType, ApprovalStatusTypeByBillCom, ApprovalStatusTypeByName } from '../../../enums/approval-status-type';
import { context } from '@opentelemetry/api';

class IdsQueryParams {

    @IsString()
    id: string
}

//note:config
const CONFIG_HYPERWALLET_ENABLED = process.env.HYPERWALLET_ENABLED;

export const HYPERWALLET_ATTRIBUTION = "To view the attribution letter for this donation, please visit https://charity-portal.givingafoundation.org/donation-lookup"

const CarbonClickHardCode: HydratedCharity = {
    id: -2,
    ein: '521601960',
    charity_name: 'Earthshare Carbon Click',
    tag_line: null,
    mission: null,
    general_email: null,
    subsection: null,
    category_id: null,
    cause_id: null,
    unlisted: false,
    overall_rating: null,
    overall_score: null,
    address_address: '7735 Old Georgetown Road',
    address_address2: 'Suite 510',
    address_city: 'Bethesda',
    address_state_abbreviation: 'MD',
    address_state_id: null,
    address_state_display_name: 'Maryland',
    address_country_id: 233,
    address_country_code: 'USA',
    address_country_display_name: 'United States of America',
    address_country_currency_code: 'USD',
    address_zip: '20814',
    address_phone: '',
    address_fax: '',
    foundation_status: null,
    ed_expiration_date: null,
    charity_group: null,
    charity_source: null,
    charity_category_id: null,
    charity_category_display_name: null,
    charity_category_icon_url: null,
    charity_cause_id: null,
    charity_cause_display_name: null,
    cob_name: null,
    cob_title: null,
    current_ceo_name: null,
    current_ceo_title: null,
    board_list_status: null,
    staff_list_status: null,
    audited_financial_status: null,
    form990_status: null,
    created: new Date(),
    updated: new Date(),
}

@UsePipes(new ValidationPipe({
    transform: true
}))
@UseFilters(ErrorFilter)
@Controller()
export class PayoutsController {
    constructor(
        private honeycombService: HoneycombService,
        private authService: AuthService,
        private prisma: PrismaService,
        private transactionDAO: TransactionsDao,
        private featureFlagDAO: FeatureFlagDao,
        private creditCardDAO: CreditCardDao,
        private employeeDAO: EmployeesDao,
        private charityDAO: CharitiesDao,
        private charityHyperwalletUserDAO: CharityHyperwalletUserDao,
        private corporationWebhookService: CorporationWebhookService,
        private earthshareBillComService: EarthshareBillComService,
        private givingaBillComService: GivingaBillComService,
        private transactionDisbursementDAO: TransactionDisbursementDao,
        private hyperwalletService: HyperwalletService,
        private charityBillComVendorDAO: CharityBillComVendorDao,
        private emailService: EmailService,
    ) {}

    @Post('/jobs/bill-com/refresh-manual-ids')
    @HttpCode(200)
    async hardRefreshAllBillComById(
        @Req() request: Request,
        @Query(new ValidationPipe({transform: true})) params: IdsQueryParams,
    ): Promise<any> {
        return this.honeycombService.start(request, {}, async () => {
            return this.authService.authorizeSuper(request, async (req) => {
                await this.refreshManualBillCom(async () => {
                    return this.transactionDisbursementDAO.readBatch(this.prisma, params.id.split(",").map((i) => parseInt(i)));
                });
                return {
                    message: 'Success'
                }
            });
        });
    }    

    @Post('/jobs/bill-com/refresh-manual')
    @HttpCode(200)
    async hardRefreshAllBillCom(
        @Req() request: Request,
        @Query('start', new ParseIntPipe()) start: number,
        @Query('end', new ParseIntPipe()) end: number,
    ): Promise<any> {
        return this.honeycombService.start(request, {}, async () => {
            return this.authService.authorizeSuper(request, async (req) => {
                if (!start || !end) {
                    throw new APIError(400, { message: "start and end are required parameters"});
                }
                await this.refreshManualBillCom(async () => {
                    return this.transactionDisbursementDAO.readRange(this.prisma, start, end)
                });
                return {
                    message: 'Success'
                }
            });
        });
    }

    private async refreshManualBillCom(f: () => Promise<TransactionDisbursement[]>) {
        const sessionId = await this.givingaBillComService.getSession();
        const disbursements = await f();
        const errors: Error[] = [];

        for(const disbursement of disbursements) {
            const updated = await this.refreshForTransactionDisbursement(disbursement, sessionId).catch((e) => {
                errors.push(e);
                return null;
            });
        }

        if(errors.length > 0) {
            const body = errors.slice(0, 100).map((err) => (`  * ${err.name} ${err.message}`)).join("\n");
            Sentry.captureException(errors[0]);

            await this.emailService.sendText({
                subject: `Givinga: Error refreshing Bill.com transactions (manual): ${errors.length} errors`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `${errors.length} errors out of ${disbursements.length} transactions. Details:\n\n${body}`
            });
        } else {
            await this.emailService.sendText({
                subject: `Givinga: Successfully refreshed ${disbursements.length} Bill.com transactions (manual)`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `Successfully refreshed ${disbursements.length} payouts.`
            });
        }
    }

    private async refreshForTransactionDisbursement(transactionDisbursement: TransactionDisbursement, sessionId: string) {
        const billId = transactionDisbursement.bill_id;
        const transactionId = transactionDisbursement.transaction_id;
        this.honeycombService.log("Refreshing Bill for Disbursement", { "bill_id": billId, "transaction_id": transactionId} );
        
        return this.prisma.$transaction(async (prisma: PrismaClient) => {
            if(!shouldUpdate(transactionDisbursement)) {
                this.honeycombService.log("Skipping transaction", { transaction_id: transactionId, bill_id: billId});
                return null;
            }

            const bill = await this.givingaBillComService.getBillById(sessionId, billId)

            this.honeycombService.log("Updating status for transaction", {
                "transaction_id": transactionId,
                "bill_id": billId,
                "payment_status": bill.paymentStatus,
                "approval_status": bill.approvalStatus
            });

            const updated = await this.transactionDisbursementDAO.updatePaymentStatus(
                prisma,
                transactionId,
                PaymentStatusTypeByBillCom.get(bill.paymentStatus) as PaymentStatusType,
                ApprovalStatusTypeByBillCom.get(bill.approvalStatus) as ApprovalStatusType,
                new Date()
            );

            if(!updated) {
                return null;
            }

            const billPay = await this.givingaBillComService.listBillPayByBillId(sessionId, bill.id).catch((e: APIError) => {
                // pre-catch
                return null;
            });

            if(billPay) {
                this.honeycombService.log('Got billPay', {
                    [HoneycombKeys.PAYLOAD]: JSON.stringify(billPay).slice(0, 10000)
                })
            }

            let clearedDate: LocalDate | null = null;
            if(billPay?.sentPayId) {
                const resp = await this.givingaBillComService.getDisbursementBySentPay(sessionId, billPay.sentPayId);
                if(resp) {
                    this.honeycombService.log('Got sentPay', {
                        [HoneycombKeys.PAYLOAD]: JSON.stringify(resp).slice(0, 10000)
                    })
                }
                clearedDate = resp?.checkData?.clearedDate ? LocalDate.parse(resp?.checkData?.clearedDate) : null;
            }

            if (billPay) {
                this.honeycombService.log(
                    'Updating payment method for disbursement',
                    {
                        transaction_id: transactionId,
                        payment_method: billPay.paymentType,
                        cleared_date: clearedDate?.toString() || '',
                    }
                );

                const maybePaymentMethod = PaymentMethodTypeByBillCom.get(billPay.paymentType)?.id;
                const paymentMethod = (maybePaymentMethod != null) ? maybePaymentMethod : PaymentMethodType.Unknown as PaymentMethodType
                const clearedDateConv = clearedDate ? convert(clearedDate).toDate() : null;
                
                await this.transactionDisbursementDAO.updateDisbursementStatus(
                    prisma,
                    transactionId,
                    paymentMethod,
                    clearedDateConv,
                )
                
                updated.payment_method_type_id = paymentMethod;
                updated.cleared_date = clearedDateConv;
            }

            return updated;
        });
    }


    @Post('/jobs/hyperwallet/refresh')
    @HttpCode(200)
    async refreshAllHyperwallet(
        @Req() request: Request
    ): Promise<any> {
        return this.authService.authorizeSuperNoHoney(request, async (req) => {
            this.honeycombService.start(request, {}, async () => {
                return this.honeycombService.initializeSuperAuth(req, async () => {
                    return this.refreshAllDisbursementsHyperwallet();
                });
            });
            return {
                message: 'Successfully queued hyperwallet refresh job'
            }
        });
    }

    async refreshAllDisbursementsHyperwallet() {
        const ScanSize = 1000;

        let lastId: number = 0;
        let hasNext: boolean = true;
        let count: number = 0;
        const errors: Error[] = [];

        // need to keep track of errors
        while (hasNext) {
            const emit = await this.transactionDisbursementDAO.listPendingHyperwalletDisbursements(this.prisma, lastId, ScanSize);
            for (const disbursement of emit) {
                const updated = await this.hyperWalletRefreshDisbursement(disbursement).catch((e) => {
                    errors.push(e)
                    return null;
                });
                count ++;
            }

            hasNext = (emit.length === ScanSize);
            lastId = _.maxBy(emit, (e) => (e.transaction_id))?.transaction_id  || 0;
        }

        if(errors.length > 0) {
            const body = errors.slice(0, 100).map((err) => (`  * ${err.name} ${err.message}`)).join("\n");
            Sentry.captureException(errors[0]);

            await this.emailService.sendText({
                subject: `Givinga: Error refreshing Hyperwallet transactions: ${errors.length} errors`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `${errors.length} errors out of ${count} transactions. Details:\n\n${body}`
            });
        } else {
            await this.emailService.sendText({
                subject: `Givinga: Successfully refreshed Hyperwallet transactions`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `Successfully refreshed ${count} payouts.`
            });
        }
    }
    
    private async hyperWalletRefreshDisbursement(disbursement: TransactionDisbursement) {
        return this.prisma.$transaction(async (prisma: PrismaClient) => {
            const status = await this.hyperwalletService.checkStatus(disbursement.bill_id);
            let updated: TransactionDisbursement
            if(status === "COMPLETED") {
                updated = await this.transactionDisbursementDAO.updatePaymentStatus(
                    prisma,
                    disbursement.transaction_id,
                    PaymentStatusType.Paid,
                    ApprovalStatusType.Approved,
                    new Date()
                );
            } else if (["CANCELLED", "FAILED", "EXPIRED", "RECALLED", "RETURNED"].includes(status)) {
                updated = await this.transactionDisbursementDAO.updatePaymentStatus(
                    prisma,
                    disbursement.transaction_id,
                    PaymentStatusType.Error,
                    ApprovalStatusType.UnderReview,
                    new Date()
                );
            } else {
                updated = await this.transactionDisbursementDAO.updatePaymentStatus(
                    prisma,
                    disbursement.transaction_id,
                    PaymentStatusType.Pending,
                    ApprovalStatusType.Pending,
                    new Date()
                );
            }

            await this.pushUpdatedWebhook(prisma, disbursement);

            return updated;
        })
    }  

    @Post('/jobs/earthshare-bill-com/refresh')
    @HttpCode(200)
    async refreshAllEarthshareBillCom(
        @Req() request: Request,
        @Query('daysAgo', new ParseIntPipe()) daysAgo: number | null
    ): Promise<any> {
        return this.authService.authorizeSuperNoHoney(request, async (req) => {
            this.honeycombService.start(request, {}, async () => {
                return this.honeycombService.initializeSuperAuth(req, async () => {
                    return this.refreshAllDisbursementsEarthshareBillCom(daysAgo || 1);
                });
            });
            return {
                message: 'Successfully queued Earthshare Bill.com refresh job'
            }
        });
    }

    async refreshAllDisbursementsEarthshareBillCom(daysAgo: number) {
        const sessionId = await this.earthshareBillComService.getSession();
        const startDate = LocalDateTime.now().minusDays(daysAgo).withHour(0).withMinute(0).withSecond(0).withNano(0);
        const lastUpdatedFrom = convert(startDate).toDate();

        const PAGE_SIZE = 100;

        let offset: number = 0;
        let total: number = 0;
        let hasNext: boolean = true;
        const errors: Error[] = [];
        while (hasNext) {
            const emit = await this.earthshareBillComService.getBillsUpdatedAfterDate(sessionId, offset, PAGE_SIZE, lastUpdatedFrom);

            this.honeycombService.log('Got Bill.com bills to update', {size: emit.length});
    
            for(const bill of emit) {
                const updated = await this.refreshForEarthshareBill(bill, sessionId).catch((e) => {
                    errors.push(e);
                    return null;
                });
            }

            total += emit.length;
            hasNext = (emit.length >= PAGE_SIZE);
            offset = offset + PAGE_SIZE;
        }

        if(errors.length > 0) {
            const body = errors.slice(0, 100).map((err) => (`  * ${err.message}`)).join("\n");
            Sentry.captureException(errors[0])

            await this.emailService.sendText({
                subject: `Givinga: Error refreshing Earthshare Bill.com transactions: ${errors.length} errors`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `${errors.length} errors out of ${total} transactions. Details:\n\n${body}`
            })
        } else {
            await this.emailService.sendText({
                subject: `Givinga: Successfully refreshed ${total} Earthshare Bill.com transactions`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `Givinga: Successfully refreshed ${total} Earthshare Bill.com transactions`
            })
        }        
    }

    private async refreshForEarthshareBill(bill: Bill, sessionId: string): Promise<TransactionDisbursement | null> {
        this.honeycombService.log("Refreshing bill", { bill_id: bill.id, transaction_id: bill.invoiceNumber});
        return this.prisma.$transaction(async (prisma: PrismaClient) => {
            const disbursement = await this.transactionDisbursementDAO.readByBillId(prisma, bill.id, DisbursementType.BillCom);
            if(!disbursement) {
                return null;
            }
            const transactionId = disbursement.transaction_id;

            if (!shouldUpdate(disbursement)) {
                this.honeycombService.log('Skipping transaction', { transaction_id: transactionId, bill_id: bill.id});
                return null;
            }

            this.honeycombService.log(
                'Updating status for transaction',
                {
                    transaction_id: transactionId,
                    bill_id: bill.id,
                    payment_status: bill.paymentStatus,
                    approval_status: bill.approvalStatus,
                }
            )

            const updated = await this.transactionDisbursementDAO.updatePaymentStatus(
                prisma,
                transactionId,
                PaymentStatusTypeByBillCom.get(bill.paymentStatus) as PaymentStatusType,
                ApprovalStatusTypeByBillCom.get(bill.approvalStatus) as ApprovalStatusType,
                new Date()
            );

            if(!updated) {
                return null;
            }

            const billPay = await this.earthshareBillComService.listBillPayByBillId(sessionId, bill.id).catch((e: APIError) => {
                // pre-catch
                return null;
            });

            if(billPay) {
                this.honeycombService.log('Got billPay', {
                    [HoneycombKeys.PAYLOAD]: JSON.stringify(billPay).slice(0, 10000)
                })
            }

            let clearedDate: LocalDate | null = null;
            if(billPay?.sentPayId) {
                const resp = await this.earthshareBillComService.getDisbursementBySentPay(sessionId, billPay.sentPayId);
                if(resp) {
                    this.honeycombService.log('Got sentPay', {
                        [HoneycombKeys.PAYLOAD]: JSON.stringify(resp).slice(0, 10000)
                    })
                }
                clearedDate = resp?.checkData?.clearedDate ? LocalDate.parse(resp?.checkData?.clearedDate) : null;
            }

            if (billPay) {
                this.honeycombService.log(
                    'Updating payment method for disbursement',
                    {
                        transaction_id: transactionId,
                        payment_method: billPay.paymentType,
                        cleared_date: clearedDate?.toString() || '',
                    }
                );

                const maybePaymentMethod = PaymentMethodTypeByBillCom.get(billPay.paymentType)?.id;
                const paymentMethod = (maybePaymentMethod != null) ? maybePaymentMethod : PaymentMethodType.Unknown as PaymentMethodType
                const clearedDateConv = clearedDate ? convert(clearedDate).toDate() : null;
                
                await this.transactionDisbursementDAO.updateDisbursementStatus(
                    prisma,
                    transactionId,
                    paymentMethod,
                    clearedDateConv,
                )
                
                updated.payment_method_type_id = paymentMethod;
                updated.cleared_date = clearedDateConv;
                
            }

            await this.pushUpdatedWebhook(prisma, updated);

            return updated;
        });
    }    

    @Post('/jobs/bill-com/refresh')
    @HttpCode(200)
    async refreshAllBillCom(
        @Req() request: Request,
        @Query('daysAgo', new ParseIntPipe()) daysAgo: number | null
    ): Promise<any> {
        return this.authService.authorizeSuperNoHoney(request, async (req) => {
            this.honeycombService.start(request, {}, async () => {
                return this.honeycombService.initializeSuperAuth(req, async () => {
                    return this.refreshAllDisbursementsBillCom(daysAgo || 1).catch(async (e) => {
                        console.error(e);
                        Sentry.captureException(e);
                        await this.emailService.sendText({
                            subject: `Givinga: Error refreshing Bill.com transactions.`,
                            from: CONST_NOTIFICATIONS_EMAIL,
                            to: CONST_NOTIFICATIONS_EMAIL,
                            body: `Error: ${(e.body && JSON.stringify(e.body)) || e.message}`
                        });
                    });
                });
            });
            return {
                message: 'Successfully queued Bill.com refresh job'
            }
        });
    }

    async refreshAllDisbursementsBillCom(daysAgo: number) {
        const sessionId = await this.givingaBillComService.getSession();
        const startDate = LocalDateTime.now().minusDays(daysAgo).withHour(0).withMinute(0).withSecond(0).withNano(0);
        const lastUpdatedFrom = convert(startDate).toDate();

        const PAGE_SIZE = 50;

        let offset: number = 0;
        let total: number = 0;
        let hasNext: boolean = true;
        const errors: Error[] = [];
        while (hasNext) {
            const emit = await this.givingaBillComService.getBillsUpdatedAfterDate(sessionId, offset, PAGE_SIZE, lastUpdatedFrom);

            this.honeycombService.log('Got Bill.com bills to update', {size: emit.length});
    
            for(const bill of emit) {
                const updated = await this.refreshForBill(bill, sessionId).catch((e) => {
                    errors.push(e);
                    return null;
                });
            }

            total += emit.length;
            hasNext = (emit.length >= PAGE_SIZE);
            offset = offset + PAGE_SIZE;

            // sleep for 10s after every batch of 50
            this.honeycombService.log('Bill.com sleep');
            await new Promise(f => setTimeout(f, 10000));
        }

        if(errors.length > 0) {
            const body = errors.slice(0, 100).map((err) => (`  * ${err.message}`)).join("\n");
            Sentry.captureException(errors[0])

            await this.emailService.sendText({
                subject: `Givinga: Error refreshing Bill.com transactions: ${errors.length} errors`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `${errors.length} errors out of ${total} transactions. Details:\n\n${body}`
            })
        } else {
            await this.emailService.sendText({
                subject: `Givinga: Successfully refreshed ${total} Bill.com transactions`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `Givinga: Successfully refreshed ${total} Bill.com transactions`
            })
        }
    }

    private async refreshForBill(bill: Bill, sessionId: string): Promise<TransactionDisbursement | null> {
        this.honeycombService.log("Refreshing bill", { bill_id: bill.id, transaction_id: bill.invoiceNumber});
        return this.prisma.$transaction(async (prisma: PrismaClient) => {
            const disbursement = await this.transactionDisbursementDAO.readByBillId(prisma, bill.id, DisbursementType.BillCom);
            if(!disbursement) {
                return null;
            }
            const transactionId = disbursement.transaction_id;

            if (!shouldUpdate(disbursement)) {
                this.honeycombService.log('Skipping transaction', { transaction_id: transactionId, bill_id: bill.id});
                return null;
            }

            this.honeycombService.log(
                'Updating status for transaction',
                {
                    transaction_id: transactionId,
                    bill_id: bill.id,
                    payment_status: bill.paymentStatus,
                    approval_status: bill.approvalStatus,
                }
            );

            const updated = await this.transactionDisbursementDAO.updatePaymentStatus(
                prisma,
                transactionId,
                PaymentStatusTypeByBillCom.get(bill.paymentStatus) as PaymentStatusType,
                ApprovalStatusTypeByBillCom.get(bill.approvalStatus) as ApprovalStatusType,
                new Date()
            );

            if (!updated) {
                return null
            }

            const billPay = await this.givingaBillComService.listBillPayByBillId(sessionId, bill.id).catch((e: APIError) => {
                // pre-catch
                console.error(e);
                return null;
            });

            if(billPay) {
                this.honeycombService.log('Got billPay', {
                    [HoneycombKeys.PAYLOAD]: JSON.stringify(billPay).slice(0, 10000)
                })
            }

            let clearedDate: LocalDate | null = null;
            if(billPay?.sentPayId) {
                const resp = await this.givingaBillComService.getDisbursementBySentPay(sessionId, billPay.sentPayId);
                if(resp) {
                    this.honeycombService.log('Got sentPay', {
                        [HoneycombKeys.PAYLOAD]: JSON.stringify(resp).slice(0, 10000)
                    })
                }
                clearedDate = resp?.checkData?.clearedDate ? LocalDate.parse(resp?.checkData?.clearedDate) : null;
            }

            if (billPay) {
                this.honeycombService.log(
                    'Updating payment method for disbursement',
                    {
                        transaction_id: transactionId,
                        payment_method: billPay.paymentType,
                        cleared_date: clearedDate?.toString() || '',
                    }
                );

                const maybePaymentMethod = PaymentMethodTypeByBillCom.get(billPay.paymentType)?.id;
                const paymentMethod = (maybePaymentMethod != null) ? maybePaymentMethod : PaymentMethodType.Unknown as PaymentMethodType
                const clearedDateConv = clearedDate ? convert(clearedDate).toDate() : null;
                
                await this.transactionDisbursementDAO.updateDisbursementStatus(
                    prisma,
                    transactionId,
                    paymentMethod,
                    clearedDateConv,
                )
                
                updated.payment_method_type_id = paymentMethod;
                updated.cleared_date = clearedDateConv;
                
            }

            await this.pushUpdatedWebhook(prisma, updated);

            return updated;
        });
    }

    private async pushUpdatedWebhook(prisma: PrismaClient, disbursement: TransactionDisbursement) {
        const transaction = await this.transactionDAO.read(prisma, disbursement.transaction_id);
        if (!transaction) {
            return null;
        }

        const [corporationId] = await this.checkPayoutsDisabled(prisma, transaction);
        await this.corporationWebhookService.donationDisbursementUpdated(prisma, corporationId, disbursement);

        return null;
    }

    @Post('/jobs/payouts/push-offsets')
    @HttpCode(200)
    async pushOffsets(
        @Req() request: Request,
        @Query('daysAgo', new ParseIntPipe()) daysAgo: number | null
    ): Promise<any> {
        return this.authService.authorizeSuperNoHoney(request, async (req) => {
            this.honeycombService.start(request, {}, async () => {
                return this.honeycombService.initializeSuperAuth(req, async () => {
                    return this.pushNewOffsetTransactions(daysAgo || 1);
                });
            });
            return {
                message: 'Successfully queued push offsets job'
            }
        });
    }

    async pushNewOffsetTransactions(daysAgo: number) {
        return this.pushNewTransactions('offset', daysAgo, async (lastId: number, limit: number) => {
            return this.transactionDAO.listUnpaidOffsetDonationsSinceDays(this.prisma, daysAgo, lastId, limit)
        });
    }    

    @Post('/jobs/payouts/push-projects')
    @HttpCode(200)
    async pushProjects(
        @Req() request: Request,
        @Query('daysAgo', new ParseIntPipe()) daysAgo: number | null
    ): Promise<any> {
        return this.authService.authorizeSuperNoHoney(request, async (req) => {
            this.honeycombService.start(request, {}, async () => {
                return this.honeycombService.initializeSuperAuth(req, async () => {
                    return this.pushNewProjectTransactions(daysAgo || 1);
                });
            });
            return {
                message: 'Successfully queued push projects job'
            }
        });
    }

    async pushNewProjectTransactions(daysAgo: number) {
        return this.pushNewTransactions('project', daysAgo, async (lastId: number, limit: number) => {
            return this.transactionDAO.listUnpaidProjectDonationsSinceDays(this.prisma, daysAgo, lastId, limit)
        });
    }

    @Post('/jobs/payouts/push-charities')
    @HttpCode(200)
    async pushCharities(
        @Req() request: Request,
        @Query('daysAgo', new ParseIntPipe()) daysAgo: number | null
    ): Promise<any> {
        return this.authService.authorizeSuperNoHoney(request, async (req) => {
            this.honeycombService.start(request, {}, async () => {
                return this.honeycombService.initializeSuperAuth(req, async () => {
                    return this.pushNewCharityTransactions(daysAgo || 1);
                });
            });
            return {
                message: 'Successfully queue push charities job'
            }
        });
    }

    async pushNewCharityTransactions(daysAgo: number) {
        return this.pushNewTransactions('charity', daysAgo, async (lastId: number, limit: number) => {
            return this.transactionDAO.listUnpaidCharityDonationsSinceDays(this.prisma, daysAgo, lastId, limit)
        });
    }

    /**
     * Helpers
     */
    private async checkPayoutsDisabled(prisma: PrismaClient, transaction: Transaction): Promise<[number, boolean]> {
        if (transaction.from_account_type_id === FundedAccountType.Corporation) {
            const disabled = await this.featureFlagDAO.isCorporationFeatureFlagEnabled(prisma, transaction.from_account_id, FeatureFlag.DisablePayouts, false);

            return [transaction.from_account_id, disabled];
        } else if (transaction.from_account_type_id === FundedAccountType.CreditCard) {
            const creditCard = await this.creditCardDAO.read(prisma, transaction.from_account_id)
            const employee = await this.employeeDAO.readByUserUnsafe(prisma, creditCard?.owner_id || 0);
            const disabled = await this.featureFlagDAO.isCorporationFeatureFlagEnabled(prisma, employee?.corporation_id || 0, FeatureFlag.DisablePayouts, false);
            return [employee?.corporation_id || 0, disabled];
        } else if (transaction.from_account_type_id === FundedAccountType.Employee) {
            const employee = await this.employeeDAO.read(prisma, transaction.from_account_id || 0);
            const disabled = await this.featureFlagDAO.isCorporationFeatureFlagEnabled(prisma, employee?.corporation_id || 0, FeatureFlag.DisablePayouts, false);
            return [employee?.corporation_id || 0, disabled];
        } else {
            this.honeycombService.log('Cannot check for PayoutIgnore because of invalid source', {
                transaction_id: transaction.id
            });

            return [0, false];
        }
    }

    private async checkEarthsharePayout(prisma: PrismaClient, transaction: Transaction): Promise< boolean> {
        if (transaction.from_account_type_id === FundedAccountType.Corporation) {
            const esPayout = await this.featureFlagDAO.isCorporationFeatureFlagEnabled(prisma, transaction.from_account_id, FeatureFlag.EarthsharePayout, false);
            this.honeycombService.log('Checked ES payout', { corporation_id: transaction.from_account_id, es_payout: esPayout });
            return esPayout;
        } else if (transaction.from_account_type_id === FundedAccountType.CreditCard) {
            const creditCard = await this.creditCardDAO.read(prisma, transaction.from_account_id)
            const employee = await this.employeeDAO.readByUserUnsafe(prisma, creditCard?.owner_id || 0);
            const esPayout = await this.featureFlagDAO.isCorporationFeatureFlagEnabled(prisma, employee?.corporation_id || 0, FeatureFlag.EarthsharePayout, false);
            // this.honeycombService.log('Checked ES payout', { employee_id: employee?.id, es_payout: esPayout });
            return esPayout;
        } else if (transaction.from_account_type_id === FundedAccountType.Employee) {
            const employee = await this.employeeDAO.read(prisma, transaction.from_account_id || 0);
            const esPayout = await this.featureFlagDAO.isCorporationFeatureFlagEnabled(prisma, employee?.corporation_id || 0, FeatureFlag.EarthsharePayout, false);
            // this.honeycombService.log('Checked ES payout', { employee_id: employee?.id, es_payout: esPayout });
            return esPayout;
        } else {
            this.honeycombService.log('Cannot check for Earthshare Payout because of invalid source', {
                transaction_id: transaction.id
            });

            return false;
        }
    }

    private async getCharityForTransaction(prisma: PrismaClient, transaction: Transaction): Promise<[HydratedCharity | null, CharityHyperwalletUser | null, boolean]>  {
        if (transaction.to_account_type_id === FundedAccountType.Charity) {
            const charity = await this.charityDAO.readHydrated(prisma, transaction.to_account_id);
            if(!charity) {
                throw new APIError(500, { message: `Invalid charity ${transaction.to_account_id}`});
            }
            const hyperwalletUser = CONFIG_HYPERWALLET_ENABLED ? (await this.charityHyperwalletUserDAO.readNotDisabled(prisma, charity.id)): null;
            if (charity.charity_source === CharitySource.GlobalGiving) {
                return [null, null, true];
            } else if (charity.charity_source === CharitySource.CanadaHelps) {
                return [null, null, true];
            } else {
                return [charity, hyperwalletUser, false];
            }
        } else if (transaction.to_account_type_id === FundedAccountType.GlobalGivingProject) {
            return [null, null, false];
        } else if (transaction.to_account_type_id === FundedAccountType.CarbonClickOffset) {
            return [CarbonClickHardCode, null, false];
        } else {
            throw new APIError(500, { message: `Invalid destination type ${transaction.to_account_type_id}`})
        }
    }

    private async getEarthshareBillComVendorId(prisma: PrismaClient, charityId: number, charityEIN: string, charity: HydratedCharity, sessionId: string) {
        this.honeycombService.log('Checking for Earthshare Bill.com vendor', { charity_id: charityId, charity_ein: charityEIN});

        const maybeVendor = await this.earthshareBillComService.getVendorByTaxId(sessionId, charityEIN);
        return maybeVendor ? maybeVendor.id : (await this.earthshareBillComService.createVendor(sessionId, charity)).id;
    }

    private async getBillComVendorId(prisma: PrismaClient, charityId: number, charityEIN: string, charity: HydratedCharity) {
        this.honeycombService.log('Checking for Bill.com vendor', { charity_id: charityId, charity_ein: charityEIN});

        const maybeCached = await this.charityBillComVendorDAO.read(prisma, charityId);
        let result: string | null;
        if (maybeCached) {
            return maybeCached.vendor_id;
        } else if (CONFIG_HYPERWALLET_ENABLED) {
            const sessionId = await this.givingaBillComService.getSession();
            const maybeVendorId = (await this.givingaBillComService.getVendorByTaxId(sessionId, charityEIN))?.id;
            if (maybeVendorId) {
                await this.charityBillComVendorDAO.upsert(prisma, charityId, maybeVendorId);
            }
            return maybeVendorId || null;
        } else {
            const sessionId = await this.givingaBillComService.getSession();
            let vendor = await this.givingaBillComService.getVendorByTaxId(sessionId, charityEIN);

            if(!vendor) {
                const result = await this.givingaBillComService.createVendor(sessionId, charity);

                this.honeycombService.log('Created vendor', { charity_ein: charity.ein, vendor_id: result.id});

                vendor = result;
            }

            await this.charityBillComVendorDAO.upsert(prisma, charityId, vendor.id);

            return vendor.id;
        }
    }

    private async pushEarthshareBillComTransaction(prisma: PrismaClient, transaction: Transaction, charity: HydratedCharity): Promise<TransactionDisbursement> {
        const sessionId = await this.earthshareBillComService.getSession();

        this.honeycombService.log('Authenticated ES Bill.com', { transaction_id: transaction.id, session_id: sessionId });

        return this.honeycombService.span('Pushing Earthshare Bill.com transaction', { transaction_id: transaction.id }, async () => {
            let description: string | null = null;
            if (transaction.to_account_type_id === FundedAccountType.CarbonClickOffset) {
                description = "Earthshare Carbon Click Offset"
            } else if (transaction.to_account_type_id === FundedAccountType.Charity) {
                description = HYPERWALLET_ATTRIBUTION;
            }

            const vendorId = await this.getEarthshareBillComVendorId(prisma, charity.id, charity.ein, charity, sessionId);

            this.honeycombService.log('Creating Earthshare bill', { transaction_id: transaction.id});
            const result = await this.earthshareBillComService.createBill(sessionId, true, vendorId, false, {
                invoiceNumber: transaction.id.toString(),
                dueDate: transaction.created,
                invoiceDate: transaction.created,
                amount: transaction.amount,
                description,
            });

            const billId = result.response_data.id;

            if (result.response_status !== 0) {
                this.honeycombService.log('Error creating Earthshare bill', { transaction_id: transaction.id, error: JSON.stringify(result.response_data).slice(0, 500)});
                await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Error);
                throw new APIError(500, { type: 'TransactionError', message: `Error for transaction ${transaction.id}: ${result.response_data}`});
            } else if (!billId) {
                this.honeycombService.log('Error creating Earthshare bill', { transaction_id: transaction.id, error: JSON.stringify(result.response_data).slice(0, 500)});
                await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Error);
                throw new APIError(500, { type: 'TransactionError', message: `No bill id for transaction ${transaction.id}: ${result.response_data}`});
            } else {
                this.honeycombService.log('Created Earthshare Bill.com bill', { transaction_id: transaction.id, token: billId});
                await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Pending);
                const disbursement = await this.transactionDisbursementDAO.upsert(prisma, transaction.id, billId, DisbursementType.BillComEarthshare, PaymentMethodType.Unknown)
                return disbursement;
            }
        });
    }

    private async pushHyperWalletTransaction(prisma: PrismaClient, transaction: Transaction, user: CharityHyperwalletUser) {
        const billId = await this.hyperwalletService.pushTransaction(transaction, user);

        this.honeycombService.log('Created Hyperwallet purchase', {
            transaction_id: transaction.id,
            token: billId
        });

        await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Pending);
        const disbursement = await this.transactionDisbursementDAO.upsert(prisma, transaction.id, billId, DisbursementType.HyperWallet, PaymentMethodType.ACH);
        return disbursement;
    }

    private async pushBillComTransaction(prisma: PrismaClient, transaction: Transaction, vendorId: string) {
        const sessionId = await this.givingaBillComService.getSession();

        return this.honeycombService.span('Pushing Bill.com transaction', { transaction_id: transaction.id, vendor_id: vendorId}, async () => {
            let description: string | null = null;
            if (transaction.to_account_type_id === FundedAccountType.CarbonClickOffset) {
                description = "Earthshare Carbon Click Offset"
            } else if (transaction.to_account_type_id === FundedAccountType.Charity) {
                description = HYPERWALLET_ATTRIBUTION;
            }

            let isSoftgivingPayout: boolean = (transaction.to_account_id === 27435283);

            this.honeycombService.log('Creating bill', { transaction_id: transaction.id});
            const result = await this.givingaBillComService.createBill(sessionId, false, vendorId, isSoftgivingPayout, {
                invoiceNumber: transaction.id.toString(),
                dueDate: transaction.created,
                invoiceDate: transaction.created,
                amount: transaction.amount,
                description,
            });

            const billId = result.response_data.id;

            if (result.response_status !== 0) {
                this.honeycombService.log('Error creating bill', { transaction_id: transaction.id, error: JSON.stringify(result.response_data).slice(0, 500)});
                await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Error);
                throw new APIError(500, { type: 'TransactionError', message: `Error for transaction ${transaction.id}: ${result.response_data}`});
            } else if (!billId) {
                this.honeycombService.log('Error creating bill', { transaction_id: transaction.id, error: JSON.stringify(result.response_data).slice(0, 500)});
                await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Error);
                throw new APIError(500, { type: 'TransactionError', message: `No bill id for transaction ${transaction.id}: ${result.response_data}`});
            } else {
                this.honeycombService.log('Created Bill.com bill', { transaction_id: transaction.id, token: billId});
                await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Pending);
                const disbursement = await this.transactionDisbursementDAO.upsert(prisma, transaction.id, billId, DisbursementType.BillCom, PaymentMethodType.Unknown)
                return disbursement;
            }
        });
    }

    async pushHyperWalletPaperCheck(prisma: PrismaClient, transaction: Transaction, charity: HydratedCharity) {
        const billId = await this.hyperwalletService.pushPaperCheck(prisma, transaction, charity);

        this.honeycombService.log('Create Hyperwallet paper check', { transaction_id: transaction.id, token: billId});

        await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Pending);
        return this.transactionDisbursementDAO.upsert(prisma, transaction.id, billId, DisbursementType.HyperWallet, PaymentMethodType.Check);
    }

    private async pushNewTransactions(name: string, daysAgo: number, f: (lastId: number, limit: number) => Promise<Transaction[]>) {
        try {
            const startDate = LocalDateTime.now().minusDays(daysAgo).withHour(0).withMinute(0).withSecond(0).withNano(0);

            this.honeycombService.log("Found transactions to push", {
                type: name,
                start: startDate.toString()
            })

            const ScanSize = 1000;

            let lastId: number = 0;
            let hasNext: boolean = true;
            let count: number = 0;
            let ignored: number = 0;        
            const errors: Error[] = [];
            // need to keep track of errors
            while (hasNext) {
                const emit = await f(lastId, ScanSize);

                for (const transaction of emit) {
                    await this.prisma.$transaction(async (prisma: PrismaClient) => {
                        const [corporationId, disabled] = await this.checkPayoutsDisabled(prisma, transaction);                    
                        if (disabled) {
                            this.honeycombService.log("Ignoring transaction for payout due to feature flag", { transaction_id: transaction.id, corporation_id: corporationId});
                            await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Ignore);
                            ignored++;
                            return null;
                        }

                        const esPayout = await this.checkEarthsharePayout(prisma, transaction);                    

                        const [charity, hyperwalletUser, markAsPaid] = await this.getCharityForTransaction(prisma, transaction);
                        let disbursement: TransactionDisbursement | null;
                        if (charity && esPayout) {
                            // reroute to earthshare Bill.com
                            disbursement = await this.pushEarthshareBillComTransaction(prisma, transaction, charity).catch((r: Error) => {
                                console.error(r)
                                Sentry.captureException(r);
                                errors.push(r)
                                return null;
                            });
                            count++;
                        } else if (charity && hyperwalletUser) {
                            disbursement = await this.pushHyperWalletTransaction(prisma, transaction, hyperwalletUser).catch((r: Error) => {
                                console.error(r)
                                Sentry.captureException(r);
                                errors.push(r);
                                return null;
                            });
                            count++;
                        } else if (charity && !hyperwalletUser) {
                            const maybeVendor = await this.getBillComVendorId(prisma, charity.id, charity.ein, charity);
                            if (maybeVendor) {
                                disbursement = await this.pushBillComTransaction(prisma, transaction, maybeVendor).catch((r: Error) => {
                                    console.error(r)
                                    Sentry.captureException(r);
                                    errors.push(r);
                                    return null;
                                });
                            } else {
                                disbursement = await this.pushHyperWalletPaperCheck(prisma, transaction, charity).catch((r: Error) => {
                                    console.error(r);
                                    Sentry.captureException(r);
                                    errors.push(r);
                                    return null;
                                });
                            }
                            count++;
                        } else if (markAsPaid) {
                            assert(!charity, 'Charity should be null here');
                            // no action taken
                            this.honeycombService.log("Payout explicitly marked as paid because international", { transaction_id: transaction.id, corporation_id: corporationId});
                            await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Paid);
                            ignored++;
                            disbursement = null;                            
                        } else {
                            assert(!charity, 'Charity should be null here');
                            // no action taken
                            this.honeycombService.log("Payout explicitly marked as pending", { transaction_id: transaction.id, corporation_id: corporationId});
                            await this.transactionDAO.setToStatus(prisma, transaction.id, PaymentStatusType.Pending);
                            ignored++;
                            disbursement = null;
                        }

                        if(disbursement) {
                            await this.corporationWebhookService.donationDisbursementCreated(prisma, corporationId, transaction, disbursement)
                        }

                        this.honeycombService.log("Transaction fell through all conditions", { transaction_id: transaction.id, corporation_id: corporationId});
                        return null;
                    }, { timeout: 20000 }) // 20s. really don't want this to fail
                }

                hasNext = (emit.length === ScanSize);
                lastId = _.maxBy(emit, (e) => (e.id))?.id  || 0;
            }

            if (errors.length > 0) {
                const body = errors.slice(0, 100).map((err) => (`  * ${err.message}`)).join('\n');
                Sentry.captureException(errors[0]);
                
                await this.emailService.sendText({
                    subject: `Givinga: Error pushing Bill.com ${name} transactions: ${errors.length} errors`,
                    from: CONST_NOTIFICATIONS_EMAIL,
                    to: CONST_NOTIFICATIONS_EMAIL,
                    body: `${errors.length} errors out of ${count} transactions. Ignored ${ignored} payouts. Details:\n\n${body}`
                });
            } else {
                await this.emailService.sendText({
                    subject: `Givinga: Successfully pushed ${name} transactions`,
                    from: CONST_NOTIFICATIONS_EMAIL,
                    to: CONST_NOTIFICATIONS_EMAIL,
                    body: `Successfully pushed ${count} payouts from ${startDate}. Ignored ${ignored} payouts.`
                });
            }
        } catch(e) {
            console.error(e);
            console.error(e.body);
            Sentry.captureException(e);
            await this.emailService.sendText({
                subject: `Givinga: Error pushing ${name} transactions.`,
                from: CONST_NOTIFICATIONS_EMAIL,
                to: CONST_NOTIFICATIONS_EMAIL,
                body: `Error: ${e.message} ${e.body}`
            });
        }
    }
  
}
