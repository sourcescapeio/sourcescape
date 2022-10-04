import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import * as request from 'supertest';
import { MainModule } from './../src/main/main.module';
import { curl } from './lib/curl';

const PROGRAM = `
class TestService {
  start() {
    console.warn('test');
  }
}


class Test {
  constructor(private testService: TestService) {
  }

  test() {
    this.testService.start()
  }
}
`;

jest.setTimeout(30000)

describe('AppController (e2e)', () => {
  let app: INestApplication;


  beforeEach(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [MainModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    await app.init();
  });

  // yarn test:e2e -i test/app.e2e-spec.ts -t 'analyze'
  it('analyze', async() => {
    await curl(app).post(
      {
        url: '/analyze',
        headers: {
          'Content-Type': 'text/plain',
        },
        formBody: PROGRAM,
      },
      (resp, body) => {
        // console.warn(JSON.stringify(body, null, 2));
      },
    );

    await curl(app).post(
      {
        url: '/analyze',
        headers: {
          'Content-Type': 'text/plain',
        },
        formBody: 'function(){}',
        expectedStatus: 400,
      },
      (resp, body) => {
        console.warn(body);
        // console.warn(JSON.stringify(body, null, 2));
      },
    );
  })

  // yarn test:e2e -i test/app.e2e-spec.ts -t 'language_server:lifecycle'
  it('language_server:lifecycle', async() => {
    // await curl(app).post(
    //   {
    //     url: '/language-server/1',
    //     body: {
    //       'test.ts': 'function () {}',
    //     },
    //     expectedStatus: 400,
    //   },
    //   (resp, body) => {
    //     console.warn(body);
    //     expect(body.error).toBe('error while compiling');
    //   },
    // );

    await curl(app).post(
      {
        url: '/language-server/1',
        body: {
          'test.ts': 'function Test() {}',
        },
      },
      (resp, body) => {
        console.warn(body);
      },
    );

    // await curl(app).post(
    //   {
    //     url: '/language-server/1',
    //     body: {
    //       'test.ts': 'function Test() {}',
    //     },
    //     expectedStatus: 400,
    //   },
    //   (resp, body) => {
    //     expect(body.message).toBe('id already exists');
    //   },
    // );

    await curl(app).delete(
      {
        url: '/language-server/1',
      },
      (resp, body) => {},
    );
  })

  // yarn test:e2e -i test/app.e2e-spec.ts -t 'language_server:request'
  it('language_server:request', async () => {
    await curl(app).post(
      {
        url: '/language-server/1',
        body: {
          'test.ts': PROGRAM,
        },
      },
      (resp, body) => {
        console.warn(body);
      },
    );

    await curl(app).post(
      {
        url: '/language-server/1/request',
        body: {
          filename: 'test.ts',
          location: 169,
        },
      },
      (resp, body) => {
        console.warn(body);
      },
    );

    await curl(app).delete(
      {
        url: '/language-server/1',
      },
      (resp, body) => {},
    );    
  });
});
