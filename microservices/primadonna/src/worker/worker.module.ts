import { Module } from '@nestjs/common';
import { CacheModule } from '@nestjs/cache-manager';
import { LanguageService } from './language.service';
import { WorkerController } from './worker.controller';

@Module({
  imports: [CacheModule.register()],
  controllers: [WorkerController],
  providers: [LanguageService],
})
export class WorkerModule {}
