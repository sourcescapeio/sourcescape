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
import { AppService } from './services/app.service';
import { LanguageService } from './services/language.service';
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
export class AppController {
  constructor(
    private readonly appService: AppService,
    private readonly languageService: LanguageService,
  ) {}

  @Get('/health')
  healthCheck() {
    return { status: 'ok' };
  }

  @Post('/analyze')
  @HttpCode(200)
  analyze(@PlainBody() body: string) {
    try {
      return this.languageService.analyze(body);
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
    await this.languageService.startInMemoryLanguageServer(id, body);
    return {
      status: 'ok',
    };
  }

  @Delete('/language-server/:id')
  async stopLanguageServer(@Param('id') id: string) {
    await this.languageService.stopLanguageServer(id);
    return {
      status: 'ok',
    };
  }

  @Post('/language-server/:id/request')
  async languageServerRequest(@Param('id') id: string, @Body() body: any) {
    await this.languageService.languageServerRequest(
      id,
      body.filename,
      body.location,
    );

    return {
      status: 'ok',
    };
  }
}
