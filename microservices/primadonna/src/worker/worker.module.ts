import { CacheModule, Module } from '@nestjs/common';
import { LanguageService } from './language.service';
import { WorkerController } from './worker.controller';

@Module({
  imports: [CacheModule.register()],
  controllers: [WorkerController],
  providers: [LanguageService],
})
export class WorkerModule {}
