import { Module } from '@nestjs/common';
import { CacheModule } from '@nestjs/cache-manager';
import { MainController } from './main.controller';
import { AnalyzerService } from './analyzer.service';
import { SpawnerService } from './spawner.service';

@Module({
  imports: [CacheModule.register()],
  controllers: [MainController],
  providers: [AnalyzerService, SpawnerService],
})
export class MainModule {}
