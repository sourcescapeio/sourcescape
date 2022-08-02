import { INestApplication } from '@nestjs/common';

import { runBeforeAll, runBeforeEach, runAfterEach } from './lib/db';

import { Downs } from './lib/db';
import { curl } from './lib/curl';
import { Prisma, PrismaClient } from '@prisma/client';
import { API_CREDS, CHARITY2_ID, CHARITY_ID, CORPORATION_ID, SUPERUSER_CREDS } from './lib/constants';
import { FeatureFlag } from '../src/enums/feature-flag';
import { EmailService } from '../src/services/email/email.service';
import { SalesforceService } from '../src/services/salesforce/salesforce.service';
import { initializeEnv } from './lib/env';
import * as Users from './lib/users';
import { StripeService } from '../src/services/stripe/stripe.service';
import { assert } from 'console';
import Stripe from 'stripe';
import { TestingModule } from '@nestjs/testing';
import { TransactionsDao } from '../src/dao/transactions/transactions.dao';
import { PrismaService } from '../src/libs/prisma/prisma.service';
import { TransparencyTypes } from '../src/enums/transparency-types';
import { PaymentStatusType } from '../src/enums/payment-status-type';
import { TransactionType } from '../src/enums/transaction-type';
import { FundedAccountType } from '../src/enums/funded-account-type';
import { convert, DateTimeFormatter, LocalDateTime, nativeJs } from 'js-joda';
import { GlobalGivingClient } from '../src/services/global-giving/global-giving.service';
import { TasksController } from '../src/controllers/tasks/tasks/tasks.controller';
import { FundsDao } from '../src/dao/funds/funds.dao';
import { CarbonClickClient } from '../src/services/carbon-click/carbon-click.service';
import { CorporationWebhookClient } from '../src/services/corporation-webhook/corporation-webhook.service';
import { CreditCardDao } from '../src/dao/credit-card/credit-card.dao';
import { AllViews } from '../src/views';
import { TransactionSearchService } from '../src/services/transaction-search/transaction-search.service';
import { PendingStripeTransactionDao } from '../src/dao/pending-stripe-transaction/pending-stripe-transaction.dao';
import { async } from 'rxjs';
import { json } from 'stream/consumers';
import { Exception } from 'handlebars';

function instancePayload(processId: number, contactName: string, contactEmail: string, region: string, requestedAmount: string) {
    return {
        "FormID": "4448115",
        "UniqueID": "842636714",
        "Get Started": {
            "value": "",
            "type": "section"
        },
        "Organization Information": {
            "value": "",
            "type": "section"
        },
        "org_legal_name": {
            "value": `saw${processId}`,
            "type": "text"
        },
        "requested_amount": {
          "value": requestedAmount,
          "type": "number"
        },
        "Primary Contact Name": {
          "type": "text",
          "value": contactName
        },
        "Primary Contact Email": {
          "value": contactEmail,
          "type": "email"
        },
        "What geographical area is served? Please select from the following list.": {
          "type": "select",
          "value": [
            region
          ]
        },
        "org_website": {
          "value": "paw",
          "type": "text"
        },
        "org_physical_address": {
          "value": "saw",
          "type": "text"
        },
        "org_mailing_address": {
          "value": "paw",
          "type": "text"
        },
        "org_phone_number": {
          "value": "saw",
          "type": "text"
        },
        "Is the organization a registered US 501c3": {
          "value": "Yes",
          "type": "radio"
        },
        "Organization EIN or equivalent": {
          "value": "123123",
          "type": "text"
        }
    }
}

// yarn test:golden -i test/grant-management.golden.ts
describe('GrantManagement', () => {
    let app: INestApplication;
    let emailService: EmailService;
    let salesforceService: SalesforceService;
    let stripeService: StripeService;
    let module: TestingModule;
    // let prisma: PrismaClient;

    const DB_LIFECYCLE = {
        downStatements: [
            ...Downs.Transactions,
            ...Downs.User,
            ...Downs.Charity,
            ...Downs.CharityAdmin,
            ...Downs.Corporation,
            ...Downs.GrantManagement,
        ],
        files: [
            'test/snapshots/GoldenCorporations.sql',
            'test/snapshots/GoldenSuperUsers.sql',
            'test/snapshots/GoldenCharitiesSimple.sql'
        ],
        upStatements: [
        ],
    }

    beforeAll(async () => {
        await runBeforeAll();
    })

    beforeEach(async () => {
        await runBeforeEach(DB_LIFECYCLE);
        const fixtures = await initializeEnv();
        app = fixtures.app;

        emailService = fixtures.module.get<EmailService>(EmailService);
        salesforceService = fixtures.module.get<SalesforceService>(SalesforceService);

        stripeService = fixtures.module.get<StripeService>(StripeService);

        module = fixtures.module;
    });

    afterEach(async() => {
        // we actually don't do downs
        // await runAfterEach(DB_LIFECYCLE);
    });

    jest.setTimeout(30000);

    // lifecycle tests

    // yarn test:golden -i test/grant-management.golden.ts -t 'manage:lifecycle'
    it('manage:lifecycle >> can go through lifecycle appropriately', async() => {
        // create a bunch of users
        const admin1 = await Users.createAdminUser(app, {
            firstName: 'Kristen',
            lastName: 'Alvarez',
            email: 'kalvarez@gmail.com',
            remoteId: '123ABC',
            password: 'test'
        }, API_CREDS);

        const admin2 = await Users.createAdminUser(app, {
            firstName: 'Kristen',
            lastName: 'Alvarez',
            email: 'kalvarez+1@gmail.com',
            remoteId: '123ABC+1',
            password: 'test'
        }, API_CREDS);

        const admin3 = await Users.createAdminUser(app, {
            firstName: 'Kristen',
            lastName: 'Alvarez',
            email: 'kalvarez+2@gmail.com',
            remoteId: '123ABC+2',
            password: 'test'
        }, API_CREDS);

        /**
         * Set up statuses
         */
        await curl(app).put({
            url: '/api/internal/review-statuses/internal_review',
            body: {
                displayName: 'Internal review'
            },
            basicAuth: API_CREDS
        }, function(resp, json){});

        await curl(app).put({
            url: '/api/internal/review-statuses/team_review',
            body: {
                displayName: 'Team review'
            },
            basicAuth: API_CREDS
        }, function(){});

        await curl(app).put({
            url: '/api/internal/review-statuses/final_review',
            body: {
                displayName: 'Final review'
            },
            basicAuth: API_CREDS
        }, function(){});

        /**
         * Create review process
         */
        const { processId1, step1 } = await (async function() {
            const processId = await curl(app).post({
                url: '/api/internal/review-processes',
                body: {
                    "title": "Grant review process",
                    "organizationNameKey": "org_legal_name",
                    "requestedAmountKey": "requested_amount",
                    "requestorEmailKey": "Primary Contact Email",
                    "requestorNameKey": "Primary Contact Name",
                    "regionKey": "What geographical area is served? Please select from the following list."
                },
                basicAuth: API_CREDS
            }, function(resp, json) {
                return json.id;
            });

            const step1 = await curl(app).post({
                url: `/api/internal/review-processes/${processId}/steps`,
                body: {
                    statusKey: 'internal_review',
                    payload: {
                        fields: [
                            { title: "Due Diligence Question 1"},
                            { title: "Due Diligence Question 2"},
                            { title: "Due Diligence Question 3"}
                        ]
                    }
                },
                basicAuth: API_CREDS
            }, function(resp, json){
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}/steps/${step1}/assign/${admin1.employeeAccountNumber}`,
                basicAuth: API_CREDS
            }, function(resp, json) {});

            const step2 = await curl(app).post({
                url: `/api/internal/review-processes/${processId}/steps`,
                body: {
                    statusKey: 'team_review',
                    payload: {
                        fields: [
                            { title: "Due Diligence Question 1"},
                            { title: "Due Diligence Question 2"},
                            { title: "Due Diligence Question 3"}
                        ]
                    }
                },
                basicAuth: API_CREDS
            }, function(resp, json){
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}/steps/${step2}/assign/${admin2.employeeAccountNumber}`,
                basicAuth: API_CREDS
            }, function(resp, json) {});

            const step3 = await curl(app).post({
                url: `/api/internal/review-processes/${processId}/steps`,
                body: {
                    statusKey: 'final_review',
                    payload: {
                        fields: [
                            { title: "Due Diligence Question 1"},
                            { title: "Due Diligence Question 2"},
                            { title: "Due Diligence Question 3"}
                        ]
                    }
                },
                basicAuth: API_CREDS
            }, function(resp, json){
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}/steps/${step3}/assign/${admin3.employeeAccountNumber}`,
                basicAuth: API_CREDS
            }, function(resp, json) {});

            return {
                processId1: processId,
                step1
            };
        })();

        // targeting is in reverse (admin3, admin2, admin1)
        const processId2 = await (async function() {
            // initially invalid
            const processId = await curl(app).post({
                url: '/api/internal/review-processes',
                body: {
                    "title": "Grant review process",
                    "organizationNameKey": "org_legal_name_2",
                    "requestedAmountKey": "requested_amount_2",
                    "requestorEmailKey": "Primary Contact Email",
                    "requestorNameKey": "Primary Contact Name",
                    "regionKey": "fail."
                },
                basicAuth: API_CREDS
            }, function(resp, json) {
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}`,
                body: {
                    "organizationNameKey": "org_legal_name",
                    "requestedAmountKey": "requested_amount",
                    "requestorEmailKey": "Primary Contact's Email Address",
                    "regionKey": "What geographical area is served? Please select from the following list."
                },
                basicAuth: API_CREDS
            }, function(resp, json) {
                console.warn(resp.statusCode);
            });

            throw new Error("stop")

            const step1 = await curl(app).post({
                url: `/api/internal/review-processes/${processId}/steps`,
                body: {
                    statusKey: 'internal_review',
                    payload: {
                        fields: [
                            { title: "Due Diligence Question 1"},
                            { title: "Due Diligence Question 2"},
                            { title: "Due Diligence Question 3"}
                        ]
                    }
                },
                basicAuth: API_CREDS
            }, function(resp, json){
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}/steps/${step1}/assign/${admin3.employeeAccountNumber}`,
                basicAuth: API_CREDS
            }, function(resp, json) {});

            const step2 = await curl(app).post({
                url: `/api/internal/review-processes/${processId}/steps`,
                body: {
                    statusKey: 'team_review',
                    payload: {
                        fields: [
                            { title: "Due Diligence Question 1"},
                            { title: "Due Diligence Question 2"},
                            { title: "Due Diligence Question 3"}
                        ]
                    }
                },
                basicAuth: API_CREDS
            }, function(resp, json){
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}/steps/${step2}/assign/${admin2.employeeAccountNumber}`,
                basicAuth: API_CREDS
            }, function(resp, json) {});

            const step3 = await curl(app).post({
                url: `/api/internal/review-processes/${processId}/steps`,
                body: {
                    statusKey: 'final_review',
                    payload: {
                        fields: [
                            { title: "Due Diligence Question 1"},
                            { title: "Due Diligence Question 2"},
                            { title: "Due Diligence Question 3"}
                        ]
                    }
                },
                basicAuth: API_CREDS
            }, function(resp, json){
                return json.id;
            });

            await curl(app).put({
                url: `/api/internal/review-processes/${processId}/steps/${step3}/assign/${admin1.employeeAccountNumber}`,
                basicAuth: API_CREDS
            }, function(resp, json) {});

            return processId;
        })();

        /**
         * Meta gets
         */
        await curl(app).get({
            url: '/api/internal/review-statuses',
            basicAuth: API_CREDS
        }, function(resp, json) {
            expect(json.length).toBe(3)
            expect(json).toContainEqual(expect.objectContaining({
                key: 'internal_review'
            }));
            expect(json).toContainEqual(expect.objectContaining({
                key: 'team_review'
            }));
            expect(json).toContainEqual(expect.objectContaining({
                key: 'final_review'
            }));
        });

        await curl(app).get({
            url: '/api/internal/review-processes',
            basicAuth: API_CREDS
        }, function(resp, json) {
            expect(json.length).toBe(2)
            expect(json).toContainEqual(expect.objectContaining({
                id: processId1
            }));
            expect(json).toContainEqual(expect.objectContaining({
                id: processId2
            }));
        });

        await curl(app).get({
            url: `/api/internal/review-processes/${processId1}`,
            basicAuth: API_CREDS
        }, function(resp, json) {
            console.warn(json);
            expect(json.id).toBe(processId1)
            expect(json.steps).toContainEqual(expect.objectContaining({
                status: 'internal_review',
                payload: {
                    fields: expect.arrayContaining([
                        { title: "Due Diligence Question 1"},
                        { title: "Due Diligence Question 2"},
                        { title: "Due Diligence Question 3"}
                    ])
                }
            }));
        });

        /**
         * Push instances
         */
        const instanceA1 = await curl(app).post({
            url: `/api/internal/review-processes/${processId1}/instances`,
            body: instancePayload(processId1, "A1Steak", "a1@test.com", "Asia Pacific", "1000.00"),
            basicAuth: API_CREDS
        }, function(resp, json){
            return json.id;
        });

        const instanceA2 = await curl(app).post({
            url: `/api/internal/review-processes/${processId1}/instances`,
            body: instancePayload(processId1, "A2Steak", "a2@test.com", "Asia Pacific", "1000.00"),
            basicAuth: API_CREDS
        }, function(resp, json){
            return json.id;
        });


        const instanceB1 = await curl(app).post({
            url: `/api/internal/review-processes/${processId2}/instances`,
            body: instancePayload(processId2, "B1Steak", "b1@test.com", "Bay_Area", "1000.00"),
            basicAuth: API_CREDS
        }, function(resp, json){
            return json.id;
        });

        const instanceB2 = await curl(app).post({
            url: `/api/internal/review-processes/${processId2}/instances`,
            body: instancePayload(processId2, "B2Steak", "b1@test.com", "Bay_Area", "1000.00"),
            basicAuth: API_CREDS
        }, function(resp, json){
            return json.id;
        });

        await curl(app).get({
            url: '/api/internal/review-processes/instances?hidePayload=true&newFormat=true',
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.total).toBe(4);
            expect(json.instances.length).toBe(4);
            expect(json.instances[0].payload).toBe(null);
        });

        await curl(app).get({
            url: '/api/internal/review-processes/instances?offset=1&newFormat=true',
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.total).toBe(4);
            expect(json.instances.length).toBe(3);
        });        

        await curl(app).get({
            url: '/api/internal/review-processes/instances?limit=1&newFormat=true',
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.total).toBe(4);
            expect(json.instances.length).toBe(1);
        });

        await curl(app).get({
            url: `/api/internal/review-processes/instances?processId=${processId1}`,
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.length).toBe(2);
            expect(json).toContainEqual(expect.objectContaining({
                id: instanceA1
            }));
            expect(json).toContainEqual(expect.objectContaining({
                id: instanceA2
            }));
        });

        await curl(app).get({
            url: `/api/internal/review-processes/instances?forCurrentUser=true&hidePayload=true`,
            bearerAuth: admin3.token,
        }, function(resp, json) {
            expect(json.length).toBe(2);
            expect(json).toContainEqual(expect.objectContaining({
                id: instanceB1
            }));
            expect(json).toContainEqual(expect.objectContaining({
                id: instanceB2
            }));
        });

        await curl(app).get({
            url: '/api/internal/review-processes/instances?q=A1',
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.length).toBe(1);
            expect(json[0].id).toBe(instanceA1);
        });

        await curl(app).get({
            url: `/api/internal/review-processes/instances?regionKey=Bay_Area`,
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.length).toBe(2);
            expect(json).toContainEqual(expect.objectContaining({
                id: instanceB1
            }));
            expect(json).toContainEqual(expect.objectContaining({
                id: instanceB2
            }));
        });

        /**
         * should not be able to approve step unless authorized
         */
        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "statusKey": "internal_review"
            },
            bearerAuth: admin2.token,
            expectedStatus: 400
        }, function(resp, json) {
            expect(json.message).toBe('Current employee does not have permissions to modify this step')
        });

        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/decline`,
            body: {
                "statusKey": "internal_review"
            },
            bearerAuth: admin2.token,
            expectedStatus: 400
        }, function(resp, json) {
            expect(json.message).toBe('Current employee does not have permissions to modify this step')
        });

        /**
         * should not be able to approve step in wrong stage
         */
        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "statusKey": "team_review"
            },
            bearerAuth: admin2.token,
            expectedStatus: 400
        }, function(resp, json) {
            expect(json.message).toBe('Process instance is on a different step')
        });

        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/decline`,
            body: {
                "statusKey": "team_review"
            },
            bearerAuth: admin2.token,
            expectedStatus: 400
        }, function(resp, json) {
            expect(json.message).toBe('Process instance is on a different step')
        });

        /**
         * Update payload at step instance
         */
        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}`,
            body: {
                statusKey: "internal_review",
                some: "other_stuff"
            },
            bearerAuth: admin1.token
        }, function(resp, json) {
        });

        await curl(app).get({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}`,
            bearerAuth: admin1.token
        }, function(resp, json) {
            expect(json.steps.length).toBe(3);
            expect(json.steps[0].instancePayload.some).toBe("other_stuff");
        });

        /**
         * Approve first
         */
         await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "statusKey": "internal_review"
            },
            bearerAuth: admin1.token,
        }, function(resp, json) {});

        expect(emailService.sendText).toHaveBeenNthCalledWith(1, expect.objectContaining({
            subject: `Application ${instanceA1} ready for your review`,
            from: 'admin@givinga.com',
            to: admin2.email, // sent to next person in queue
            body: expect.stringContaining('Hello - This is an automated notification that you have an application that is ready for your review'),
        }));

        /**
         * Decline second instance
         */
         await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA2}/decline`,
            body: {
                "statusKey": "internal_review"
            },
            bearerAuth: admin1.token,
        }, function(resp, json) {});

        expect(emailService.sendText).toHaveBeenNthCalledWith(2, expect.objectContaining({
            subject: `Grant declined`,
            from: 'admin@givinga.com',
            to: 'a2@test.com', // sent to next person in queue
            body: 'Your grant has been declined',
        }));

        /**
         * test some searches here
         */
         await curl(app).get({
            url: `/api/internal/review-processes/instances?statusKeys=declined&forCurrentUser=true`,
            bearerAuth: admin1.token,
        }, function(resp, json) {
            expect(json.length).toBe(1);
            expect(json[0].id).toBe(instanceA2);
        });

        await curl(app).get({
            url: `/api/internal/review-processes/instances?statusKeys=declined,team_review`,
            bearerAuth: admin1.token,
        }, function(resp, json) {
            expect(json.length).toBe(2);
            expect(json[0].id).toBe(instanceA2);
            expect(json[0].status).toBe('declined');
            expect(json[1].id).toBe(instanceA1);
            expect(json[1].status).toBe('team_review');
        });

        await curl(app).get({
            url: `/api/internal/review-processes/instances?forCurrentUser=true`,
            bearerAuth: admin2.token,
        }, function(resp, json) {
            expect(json.length).toBe(1);
            expect(json[0].id).toBe(instanceA1);
        });    

        /**
         * Approve to next phase
         */
         await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "more_stuff": "for_payload",
                "statusKey": "team_review"
            },
            bearerAuth: admin2.token,
        }, function(resp, json) {});

        expect(emailService.sendText).toHaveBeenNthCalledWith(3, expect.objectContaining({
            subject: `Application ${instanceA1} ready for your review`,
            from: 'admin@givinga.com',
            to: admin3.email, // sent to next person in queue
            body: expect.stringContaining('Hello - This is an automated notification that you have an application that is ready for your review'),
        }));

        /**
         * Approve in final phase
         */
         await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "more_stuff": "for_payload",
                "statusKey": "final_review"
            },
            bearerAuth: admin3.token,
        }, function(resp, json) {});

        expect(emailService.sendText).toHaveBeenNthCalledWith(4, expect.objectContaining({
            subject: `Grant accepted`,
            from: 'admin@givinga.com',
            to: 'a1@test.com', // sent to next person in queue
            body: 'Your grant has been accepted',
        }));

        /**
         * Try some searches
         */
         await curl(app).get({
            url: `/api/internal/review-processes/instances?statusKeys=approved`,
            bearerAuth: admin1.token,
        }, function(resp, json) {
            expect(json.length).toBe(1);
            expect(json[0].id).toBe(instanceA1);
            expect(json[0].status).toBe('approved');
        });

        /**
         * Reset
         */
         await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/reset`,
            body: {
                "statusKey": "internal_review"
            },
            basicAuth: API_CREDS,
        }, function(resp, json) {});

        await curl(app).get({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}`,
            bearerAuth: admin1.token
        }, function(resp, json) {
            console.warn(json);
            expect(json.status.key).toBe('internal_review')
            expect(json.currentStepId).toBe(json.steps[0].stepId);
        });

        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "statusKey": "internal_review"
            },
            bearerAuth: admin1.token,
        }, function(resp, json) {});

        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "statusKey": "team_review"
            },
            bearerAuth: admin2.token,
        }, function(resp, json) {});

        await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}/approve`,
            body: {
                "statusKey": "final_review"
            },
            bearerAuth: admin3.token,
        }, function(resp, json) {});

        await curl(app).get({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}`,
            bearerAuth: admin1.token
        }, function(resp, json) {
            expect(json.status.key).toBe('approved')
        });

        /**
         * delete instance
         */
        await curl(app).delete({
            url: `/api/internal/review-processes/${processId1}/instances/${instanceA1}`,
            basicAuth: API_CREDS,
        }, function(resp, json) {});

        /**
         * update payload
         */
         await curl(app).put({
            url: `/api/internal/review-processes/${processId1}/steps/${step1}/payload`,
            basicAuth: API_CREDS,
            body: {
                blah: 'blah'
            }
        }, function(resp, json) {});

        await curl(app).get({
            url: `/api/internal/review-processes/${processId1}`,
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.id).toBe(processId1)
            expect(json.steps[0].payload.blah).toBe('blah')
        });

        /**
         * delete step
         */
         await curl(app).delete({
            url: `/api/internal/review-processes/${processId1}/steps/${step1}`,
            basicAuth: API_CREDS,
        }, function(resp, json) {});

        await curl(app).get({
            url: `/api/internal/review-processes/${processId1}`,
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.id).toBe(processId1)
            expect(json.steps.length).toBe(2);
            expect(json.steps[0].status).toBe('team_review');
            expect(json.steps[1].status).toBe('final_review');
        });

        /**
         * delete process
         */
         await curl(app).delete({
            url: `/api/internal/review-processes/${processId1}`,
            basicAuth: API_CREDS,
        }, function(resp, json) {});

        await curl(app).get({
            url: `/api/internal/review-processes`,
            basicAuth: API_CREDS,
        }, function(resp, json) {
            expect(json.length).toBe(1);
            expect(json[0].id).toBe(processId2)
        });
    });
});
