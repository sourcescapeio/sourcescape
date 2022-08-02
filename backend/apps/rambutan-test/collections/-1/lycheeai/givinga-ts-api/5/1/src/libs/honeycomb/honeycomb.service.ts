import { Request } from 'express';
import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Metadata, credentials } from '@grpc/grpc-js';
import { NodeSDK } from '@opentelemetry/sdk-node';
import { Resource } from '@opentelemetry/resources';
import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-grpc';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import * as api from '@opentelemetry/api';
import { ExpressLayerType } from '@opentelemetry/instrumentation-express';
import * as _ from 'lodash';
import { CharityAPIRequest, CorporateAPIRequest, LegacyAPIKeyRequest, SuperAdminAPIRequest } from '../../services/auth/auth.service';
import * as Sentry from '@sentry/minimal';

const CONFIG_HONEYCOMB_TEAM = process.env.HONEYCOMB_TEAM || ''
const CONFIG_HONEYCOMB_DATASET = process.env.HONEYCOMB_DATASET || 'ts-api-local';

console.warn('TEAM', CONFIG_HONEYCOMB_TEAM);

const metadata = new Metadata()
metadata.set('x-honeycomb-team', CONFIG_HONEYCOMB_TEAM);
metadata.set('x-honeycomb-dataset', CONFIG_HONEYCOMB_DATASET);
const traceExporter = new OTLPTraceExporter({
  url: 'grpc://api.honeycomb.io:443/',
  credentials: credentials.createSsl(),
  metadata
});

type HoneycombPrimitive = string | boolean | number;

type HoneycombObject = {
  [k: string]: HoneycombPrimitive
};

const HTTP_METHOD = api.createContextKey("HTTP_METHOD");
const HTTP_PATH = api.createContextKey("HTTP_PATH");

const AUTH_TYPE = api.createContextKey("AUTH_TYPE");
const AUTH_CORPORATION_ID = api.createContextKey("AUTH_CORPORATION_ID");
const AUTH_SUPER_USERNAME = api.createContextKey("AUTH_SUPER_USERNAME");
const AUTH_AUTH0_EMAIL = api.createContextKey("AUTH_AUTH0_EMAIL");
const AUTH_AUTH0_USER_ID = api.createContextKey("AUTH_AUTH0_USER_ID");
const AUTH_API_KEY_USERNAME = api.createContextKey("AUTH_API_KEY_USER");
const AUTH_CORPORATE_ADMIN_EMAIL = api.createContextKey("AUTH_CORPORATE_ADMIN_EMAIL");
const AUTH_CORPORATE_ADMIN_USER_ID = api.createContextKey("AUTH_CORPORATE_ADMIN_USER_ID");
const AUTH_CHARITY_USERNAME = api.createContextKey("AUTH_CHARITY_USERNAME");
const AUTH_CHARITY_ID = api.createContextKey("AUTH_CHARITY_ID");


const HTTP_METHOD_KEY = "http.method";
const HTTP_PATH_KEY = "http.path";
const AUTH_TYPE_KEY = "auth.type";
const AUTH_CORPORATION_ID_KEY = "auth.corporation_id";
const AUTH_SUPER_USERNAME_KEY = "auth.super.username";
const AUTH_AUTH0_EMAIL_KEY = "auth.auth0.email";
const AUTH_AUTH0_USER_ID_KEY = "auth.auth0.user_id";
const AUTH_API_KEY_USERNAME_KEY = "auth.api_key.username";
const AUTH_CORPORATE_ADMIN_EMAIL_KEY = "auth.corporate_admin.email";
const AUTH_CORPORATE_ADMIN_USER_ID_KEY = "auth.corporate_admin.user_id";
const AUTH_CHARITY_USERNAME_KEY = "auth.charity.username";
const AUTH_CHARITY_ID_KEY = "auth.charity.id";

export const HoneycombKeys = {
  ERROR_CAUSE: 'x.error_cause',
  PAYLOAD: 'x.payload',
  DATA: 'x.data',
  //
  FEE: 'x.fee',
  AMOUNT: 'x.amount',
  EXCHANGE_RATE: 'x.exchange_rate',
  GATEWAY_ID: 'x.gateway_id',
  TRANSACTION_ID: 'x.transaction_id',
  WEBHOOK_TYPE: 'x.webhook_type',
  ACCOUNT_NUMBER: 'x.account_number',
  ACCOUNT_USERNAME: 'x.account_username',
  FUNDED_ACCOUNT_TYPE: 'x.funded_account_type',
  FUNDED_ACCOUNT_ID: 'x.funded_account_id',
  CORPORATION_ID: 'x.corporation_id',
  TYPE: 'x.type',
  URL: 'x.url',
  EMAIL: 'x.email'
};

export const HoneycombEvents = {
  WEBHOOK_ERROR: 'webhook.consume.error',
  WEBHOOK_START: 'webhook.consume.start',
  //
  CREATE_USER_START: 'user.create.start',
  CREATE_USER_ERROR: 'user.create.error',
  //
  WEBHOOK_STATUS_WRITTEN: 'webhook.status.written',
  WEBHOOK_STATUS_START: 'webhook.status.start',
  WEBHOOK_SUBACCOUNT_WRITTEN: 'webhook.subaccount.written',
  //
  CORPORATION_WEBHOOK_PUSH: 'corporation_webhook.push',
  //
  ELASTICSEARCH_INDEXING_ERROR: 'elasticsearch.indexing.error',
  //
  EMAILS_LEGACY_FUNDED: 'emails.legacy_funded',
  EMAILS_FLAGGED_DONATION: 'emails.flagged.donation',
  EMAILS_FLAGGED_DONATION_ADMIN: 'emails.flagged.donation_admin',
  EMAILS_FLAGGED_FUNDING: 'emails.flagged.funding',
  EMAILS_FLAGGED_FUNDING_ADMIN: 'emails.flagged.funding_admin',
  EMAILS_FLAGGED_NEW_USER: 'emails.flagged.new_user',
  //
  SALESFORCE_REPLICATE_TRANSACTION: 'salesforce.replicate.transaction',
  SALESFORCE_REPLICATE_EMPLOYEE: 'salesforce.replicate.employee',
  //
  MATCH_REQUEST_CREATED: 'match.request.created',
  MATCH_REQUEST_APPROVED: 'match.request.approved',
  MATCH_APPROVAL_AUTO: 'match.request.auto-approved',
  MATCH_APPROVAL_SKIP: 'match.request.approval-skip',
  //
  DONATION_START: 'donation.start',
  DONATION_ERROR: 'donation.error',
  //
  CORPORATE_DONATION_START: 'corporate_donation.start',
  CORPORATE_DONATION_ERROR: 'corporate_donation.error'
};

type KEYS_MAP_KEY = typeof HTTP_METHOD_KEY | typeof HTTP_PATH_KEY | 
  typeof AUTH_TYPE_KEY | typeof AUTH_CORPORATION_ID_KEY | typeof AUTH_SUPER_USERNAME_KEY | 
  typeof AUTH_AUTH0_EMAIL_KEY | typeof AUTH_AUTH0_USER_ID_KEY | typeof AUTH_API_KEY_USERNAME_KEY |
  typeof AUTH_CORPORATE_ADMIN_EMAIL_KEY | typeof AUTH_CORPORATE_ADMIN_USER_ID_KEY;

const KEYS_MAP = {
  [HTTP_METHOD_KEY]: HTTP_METHOD,
  [HTTP_PATH_KEY]: HTTP_PATH,
  [AUTH_TYPE_KEY]: AUTH_TYPE,
  [AUTH_CORPORATION_ID_KEY]: AUTH_CORPORATION_ID,
  [AUTH_SUPER_USERNAME_KEY]: AUTH_SUPER_USERNAME,
  [AUTH_AUTH0_EMAIL_KEY]: AUTH_AUTH0_EMAIL,
  [AUTH_AUTH0_USER_ID_KEY]: AUTH_AUTH0_USER_ID,
  [AUTH_API_KEY_USERNAME_KEY]: AUTH_API_KEY_USERNAME,
  [AUTH_CORPORATE_ADMIN_EMAIL_KEY]: AUTH_CORPORATE_ADMIN_EMAIL,
  [AUTH_CORPORATE_ADMIN_USER_ID_KEY]: AUTH_CORPORATE_ADMIN_USER_ID,
  [AUTH_CHARITY_USERNAME_KEY]: AUTH_CHARITY_USERNAME,
  [AUTH_CHARITY_ID_KEY]: AUTH_CHARITY_ID,
};

const sdk = new NodeSDK({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: 'givinga-api',
  }),
  traceExporter,
  instrumentations: [getNodeAutoInstrumentations({
    '@opentelemetry/instrumentation-http': {
    },
    '@opentelemetry/instrumentation-express': {
      ignoreLayersType: [ExpressLayerType.MIDDLEWARE, ExpressLayerType.REQUEST_HANDLER]
    },
    '@opentelemetry/instrumentation-pg': {},
    // disable everything else
    '@opentelemetry/instrumentation-aws-lambda': {
      enabled: false
    },
    '@opentelemetry/instrumentation-aws-sdk': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-bunyan': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-cassandra-driver': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-connect': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-dns': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-fastify': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-generic-pool': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-graphql': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-grpc': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-hapi': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-ioredis': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-knex': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-koa': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-memcached': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-mongodb': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-mysql2': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-mysql': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-nestjs-core': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-net': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-pino': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-redis': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-restify': {
      enabled: false,
    },
    '@opentelemetry/instrumentation-winston': {
      enabled: false,
    },
  })]
});

@Injectable()
export class HoneycombService 
  implements OnModuleInit, OnModuleDestroy {
  
  /**
   * Lifecycle
   */
  async onModuleInit() {
    await sdk.start()
      .catch((error) => console.log('Error initializing tracing', error));
    
    console.log('Tracing initialized');
  }
  
  async onModuleDestroy() {
    await sdk.shutdown()
      .catch((error) => console.log('Error terminating tracing', error))

    console.log('Tracing terminated')
  }
  
  /**
   * Should only be called from AuthServices
   */
  async initializeCorporateAuth<T>(req: CorporateAPIRequest, f: (r: CorporateAPIRequest) => Promise<T>): Promise<T> {
    const baseContext = api.context.active()
      .setValue(AUTH_TYPE, "corporate")
      .setValue(AUTH_CORPORATION_ID, req.corporationId.toString());

    let newContext: api.Context = baseContext;
    if (req.superAdmin) {
      newContext = baseContext
        .setValue(AUTH_SUPER_USERNAME, req.superAdmin.email)
    } else if (req.auth0User && req.auth0Employee) {
      newContext = baseContext
        .setValue(AUTH_AUTH0_EMAIL, req.auth0User.email)
        .setValue(AUTH_AUTH0_USER_ID, req.auth0User.id.toString())
    } else if (req.apiKey) {
      newContext = baseContext
        .setValue(AUTH_API_KEY_USERNAME, req.apiKey.username)
    } else if (req.corporateAdmin) {
      newContext = baseContext
        .setValue(AUTH_CORPORATE_ADMIN_EMAIL, req.corporateAdmin.email)
        .setValue(AUTH_CORPORATE_ADMIN_USER_ID, req.corporateAdmin.id.toString())
    }    

    return api.context.with(newContext, () => {
      return this.span('request-auth', {}, async () => (f(req)));
    });
  }

  async initializeSuperAuth<T>(req: SuperAdminAPIRequest, f: (r: SuperAdminAPIRequest) => Promise<T>): Promise<T> {
    const baseContext = api.context.active()
      .setValue(AUTH_TYPE, "superuser")
      .setValue(AUTH_SUPER_USERNAME, req.superAdmin.email);

    return api.context.with(baseContext, () => {
      return this.span('request-auth', {}, async () => (f(req)));
    });
  }  

  async initializeCharityAuth<T>(req: CharityAPIRequest, f: (r: CharityAPIRequest) => Promise<T>): Promise<T> {
    const baseContext = api.context.active()
      .setValue(AUTH_TYPE, "charity");

    let newContext: api.Context = baseContext;    
    if (req.superAdmin) {
      newContext = baseContext
        .setValue(AUTH_SUPER_USERNAME, req.superAdmin.email)
    } else if (req.charityUser) {
      newContext = baseContext
        .setValue(AUTH_CHARITY_ID, req.charityUser?.id)
        .setValue(AUTH_CHARITY_USERNAME, req.charityUser?.email);      
    }

    return api.context.with(newContext, () => {
      return this.span('request-auth', {}, async () => (f(req)));
    });
  }

  async initializeLegacy<T>(req: LegacyAPIKeyRequest, f: (r: LegacyAPIKeyRequest) => Promise<T>): Promise<T> {
    const baseContext = api.context.active()
      .setValue(AUTH_TYPE, "legacy")
      .setValue(AUTH_API_KEY_USERNAME, req.apiUser.username);

    return api.context.with(baseContext, () => {
      return this.span('request-auth', {}, async () => (f(req)));
    });
  }

  /**
   * Truly public methods
   */
  async start<T>(req: Request, attributes: HoneycombObject, f: () => Promise<T>): Promise<T> {
    const activeContext = api.context.active();
    const newContext = activeContext
      .setValue(HTTP_METHOD, req.method)
      .setValue(HTTP_PATH, req.url)
    return api.context.with(newContext, () => {
      return this.span('start-request', attributes, f)
    });
  }
  
  async span<T>(name: string, attributes: HoneycombObject, f: () => Promise<T>): Promise<T> {
    const tracer = api.trace.getTracer("givinga-api");
    const span = tracer.startSpan(name);
    const activeContext = api.context.active();

    Object.keys(KEYS_MAP).forEach((key: KEYS_MAP_KEY) => {
      const keySymbol: symbol = KEYS_MAP[key] as symbol;
      const maybeValue = activeContext.getValue(keySymbol) as string | undefined;
      if (maybeValue) {
        span.setAttribute(key, maybeValue);
      }
    });

    _.keys(attributes).forEach(function(k) {
      span.setAttribute(k, attributes[k])
    });

    return api.context.with(api.trace.setSpan(activeContext, span), () => {
      return f().finally(function() {
        span.end()    
      });
    });
  }

  log(name: string, attributes?: HoneycombObject) {
    const activeContext = api.context.active();

    const allKeys = _.mapValues(KEYS_MAP, (key, v) => {
      return (activeContext.getValue(key) as string | undefined)
    });

    const mergedAttributes = {
      ...(attributes ?? {}),
      ...allKeys,
    };

    const activeSpan = api.trace.getSpan(activeContext);
    if (activeSpan) {
      activeSpan.addEvent(name, mergedAttributes);
    } else {
      const tracer = api.trace.getTracer("givinga-api");
      const newSpan = tracer.startSpan(name);
      newSpan.addEvent(name, mergedAttributes);
      newSpan.end();
    }
  }

  error(name: string, e: Error) {
    Sentry.captureException(e);

    this.log(name, {
      error: e.message,
      error_name: e.name,
    });
  }
}
