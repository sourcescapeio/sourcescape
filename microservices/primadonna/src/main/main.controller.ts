import {
  BadRequestException,
  Body,
  Controller,
  createParamDecorator,
  Delete,
  ExecutionContext,
  Get,
  HttpCode,
  Param,
  Post,
  Req,
} from '@nestjs/common';
import { AnalyzerService } from './analyzer.service';
import { SpawnerService } from './spawner.service';
import * as rawBody from 'raw-body';

// https://stackoverflow.com/questions/52283713/how-do-i-pass-plain-text-as-my-request-body-using-nestjs
const PlainBody = createParamDecorator(async (_, context: ExecutionContext) => {
  const req = context.switchToHttp().getRequest<import('express').Request>();
  if (!req.readable) {
    throw new BadRequestException('Invalid body');
  }

  const body = (await rawBody(req)).toString('utf8').trim();
  return body;
});

@Controller()
export class MainController {
  constructor(
    private readonly analyzerService: AnalyzerService,
    private readonly spawnerService: SpawnerService,
  ) {}

  @Get('/health')
  healthCheck() {
    return { status: 'ok' };
  }

  @Post('/analyze')
  @HttpCode(200)
  analyze(@PlainBody() body: string) {
    try {
      return this.analyzerService.analyze(body);
    } catch (e) {
      throw new BadRequestException('Error parsing')
    }
  }

  @Post('/language-server/:id')
  @HttpCode(200)
  async startLanguageServer(
    @Param('id') id: string,
    @Body() body: { [k: string]: string },
  ) {
    await this.spawnerService.startInMemoryLanguageServer(id, body);

    console.warn('STARTED LANGUAGE SERVER');
    return {
      status: 'ok',
    };
  }

  @Delete('/language-server/:id')
  async stopLanguageServer(@Param('id') id: string) {
    await this.spawnerService.stopLanguageServer(id);

    console.warn('TERMINATED LANGUAGE SERVER');
    return {
      status: 'ok',
    };
  }

  @Post('/language-server/:id/request')
  @HttpCode(200)
  async languageServerRequest(@Param('id') id: string, @Body() body: any) {
    const response = await this.spawnerService.languageServerRequest(
      id,
      body.filename,
      body.location,
    );

    console.warn(response);

    return {
      response
    };
  }
}
