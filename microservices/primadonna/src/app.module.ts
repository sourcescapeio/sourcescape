import { CacheModule, Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './services/app.service';
import { LanguageService } from './services/language.service';

@Module({
  imports: [CacheModule.register()],
  controllers: [AppController],
  providers: [AppService, LanguageService],
})
export class AppModule {}
