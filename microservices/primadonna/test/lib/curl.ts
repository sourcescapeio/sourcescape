import { INestApplication } from '@nestjs/common';
import * as request from 'supertest';
import * as fs from 'fs';
import { expect, test } from '@jest/globals';

type PostParams = {
  url: string;
  basicAuth?: [string, string];
  bearerAuth?: string;
  headers?: { [k: string]: string };
  body?: Record<string, any>;
  formBody?: string;
  attachment?: {
    key: string;
    file: string;
    filename: string;
  };
  expectedStatus?: number;
};

type GetParams = {
  url: string;
  basicAuth?: [string, string];
  bearerAuth?: string;
  headers?: { [k: string]: string };
  expectedStatus?: number;
};

class Curler {
  app: INestApplication;

  constructor(app: INestApplication) {
    this.app = app;
  }

  async post<T>(params: PostParams, f: (_: request.Response, __: any) => T) {
    let req = request(this.app.getHttpServer()).post(params.url);

    if (params.body) {
      req = req.send(params.body);
    }

    if (params.formBody) {
      req = req.send(params.formBody);
    }

    if (params.headers) {
      req = req.set(params.headers);
    }

    if (params.basicAuth) {
      const [user, pass] = params.basicAuth;
      req = req.auth(user, pass);
    }

    if (params.bearerAuth) {
      req = req.set({ Authorization: `Bearer ${params.bearerAuth}` });
    }

    if (params.attachment) {
      const { key, filename, file } = params.attachment;
      req = req.attach(key, fs.readFileSync(file), filename);
    }

    const response = await req;

    if (response.status !== (params.expectedStatus || 200)) {
      console.error('Wrong status', response.status, response.body);
    }

    expect(response.status).toEqual(params.expectedStatus || 200);

    return f(response, response.body);
  }

  async put<T>(params: PostParams, f: (_: request.Response, __: any) => T) {
    let req = request(this.app.getHttpServer())
      .put(params.url)
      .send(params.body || {});

    if (params.headers) {
      req = req.set(params.headers);
    }

    if (params.basicAuth) {
      const [user, pass] = params.basicAuth;
      req = req.auth(user, pass);
    }

    if (params.bearerAuth) {
      req = req.set({ Authorization: `Bearer ${params.bearerAuth}` });
    }

    const response = await req;

    if (response.status !== (params.expectedStatus || 200)) {
      console.error('Wrong status', response.status, response.body);
    }

    expect(response.status).toEqual(params.expectedStatus || 200);

    return f(response, response.body);
  }

  async delete<T>(params: GetParams, f: (_: request.Response, __: any) => T) {
    let req = request(this.app.getHttpServer()).delete(params.url);

    if (params.headers) {
      req = req.set(params.headers);
    }

    if (params.basicAuth) {
      const [user, pass] = params.basicAuth;
      req = req.auth(user, pass);
    }

    if (params.bearerAuth) {
      req = req.set({ Authorization: `Bearer ${params.bearerAuth}` });
    }

    const response = await req;

    if (response.status !== (params.expectedStatus || 200)) {
      console.error('Wrong status', response.status, response.body);
    }

    expect(response.status).toEqual(params.expectedStatus || 200);

    return f(response, response.body);
  }

  async get<T>(params: GetParams, f: (_: request.Response, __: any) => T) {
    let req = request(this.app.getHttpServer()).get(params.url);

    if (params.headers) {
      req = req.set(params.headers);
    }

    if (params.basicAuth) {
      const [user, pass] = params.basicAuth;
      req = req.auth(user, pass);
    }

    if (params.bearerAuth) {
      req = req.set({ Authorization: `Bearer ${params.bearerAuth}` });
    }

    const response = await req;

    if (response.status !== (params.expectedStatus || 200)) {
      console.error('Wrong status', response.status, response.body);
    }

    expect(response.status).toEqual(params.expectedStatus || 200);

    return f(response, response.body);
  }
}

export function curl(app: INestApplication) {
  return new Curler(app);
}
